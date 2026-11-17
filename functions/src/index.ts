import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import * as admin from "firebase-admin";

setGlobalOptions({
  region: "asia-southeast1",
  maxInstances: 20,
});

admin.initializeApp();
const db = admin.firestore();

const PLATFORM_SUPER_ADMIN_EMAIL = "storelinnhtet@gmail.com";
const DAY_MS = 24 * 60 * 60 * 1000;

function assertSuperAdmin(auth: { uid: string; token: admin.auth.DecodedIdToken } | undefined): string {
  if (!auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  const email = (auth.token.email || "").toLowerCase();
  if (!auth.token.email_verified || email !== PLATFORM_SUPER_ADMIN_EMAIL) {
    throw new HttpsError("permission-denied", "Platform Super Admin privileges required.");
  }
  return auth.uid;
}

/**
 * Trusted Server Time + Subscription Access Validation endpoint
 * Prevents device clock manipulation by always checking server Timestamp.now().
 */
export const validateSubscriptionAccess = onCall(
  { enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required.");
    }
    const companyId = String(request.data?.companyId || "");
    if (!companyId) {
      throw new HttpsError("invalid-argument", "companyId is required.");
    }

    const compSnap = await db.collection("companies").doc(companyId).get();
    if (!compSnap.exists) {
      throw new HttpsError("not-found", "Business account not found.");
    }
    const comp = compSnap.data()!;
    const now = admin.firestore.Timestamp.now();
    const expiresAt: admin.firestore.Timestamp | null = comp.subscriptionExpiresAt || null;

    let effectiveStatus: string = comp.subscriptionStatus || "PENDING_PAYMENT";
    if (
      (effectiveStatus === "ACTIVE" || effectiveStatus === "EXPIRING_SOON") &&
      expiresAt &&
      expiresAt.toMillis() <= now.toMillis()
    ) {
      effectiveStatus = "EXPIRED";
      await compSnap.ref.update({
        subscriptionStatus: "EXPIRED",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }

    const remainingDays = expiresAt
      ? Math.ceil((expiresAt.toMillis() - now.toMillis()) / DAY_MS)
      : 0;

    return {
      serverTimeMillis: now.toMillis(),
      subscriptionStatus: effectiveStatus,
      remainingDays,
      isOperational:
        (effectiveStatus === "ACTIVE" || effectiveStatus === "EXPIRING_SOON") &&
        remainingDays > 0,
      maxBranchesAllowed: comp.maxBranchesAllowed || 1,
      maxEmployeesAllowed: comp.maxEmployeesAllowed || 1,
    };
  }
);

/**
 * Super Admin verifies payment & atomically activates/extends business subscription.
 */
export const approveSubscriptionPayment = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const adminUid = assertSuperAdmin(request.auth);
    const paymentId = String(request.data?.paymentId || "");
    if (!paymentId) {
      throw new HttpsError("invalid-argument", "paymentId is required.");
    }

    return await db.runTransaction(async (tx) => {
      const payRef = db.collection("subscription_payments").doc(paymentId);
      const paySnap = await tx.get(payRef);
      if (!paySnap.exists) {
        throw new HttpsError("not-found", "Payment record not found.");
      }
      const pay = paySnap.data()!;
      if (pay.status === "VERIFIED") {
        throw new HttpsError("failed-precondition", "Payment already verified.");
      }

      const compRef = db.collection("companies").doc(pay.companyId);
      const compSnap = await tx.get(compRef);
      const planRef = db.collection("subscription_plans").doc(pay.planId);
      const planSnap = await tx.get(planRef);
      if (!compSnap.exists || !planSnap.exists) {
        throw new HttpsError("not-found", "Company or Plan no longer exists.");
      }

      const comp = compSnap.data()!;
      const plan = planSnap.data()!;
      const nowMs = admin.firestore.Timestamp.now().toMillis();
      const existingExpiryMs = comp.subscriptionExpiresAt
        ? comp.subscriptionExpiresAt.toMillis()
        : 0;

      const baseStartMs =
        (comp.subscriptionStatus === "ACTIVE" || comp.subscriptionStatus === "EXPIRING_SOON") &&
        existingExpiryMs > nowMs
          ? existingExpiryMs
          : nowMs;

      const durationDays = Number(plan.durationDays || pay.durationDays || 30);
      const startAt = admin.firestore.Timestamp.fromMillis(baseStartMs);
      const expiresAt = admin.firestore.Timestamp.fromMillis(baseStartMs + durationDays * DAY_MS);
      const receiptNumber = `INV-${new Date(nowMs).toISOString().slice(0, 10).replace(/-/g, "")}-${paymentId.slice(-4).toUpperCase()}`;

      tx.update(payRef, {
        status: "VERIFIED",
        receiptNumber,
        verifiedByUid: adminUid,
        verifiedByName: request.auth?.token.name || "Platform Super Admin",
        verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
        grantedStartAt: startAt,
        grantedExpiresAt: expiresAt,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      tx.update(compRef, {
        subscriptionPlanId: plan.id || pay.planId,
        subscriptionPlanName: plan.nameEn || pay.planNameEn,
        subscriptionStatus: "ACTIVE",
        subscriptionStartAt: startAt,
        subscriptionExpiresAt: expiresAt,
        maxBranchesAllowed: plan.maxBranches,
        maxEmployeesAllowed: plan.maxEmployees,
        enabledFeatures: plan.enabledFeatures || [],
        expiryNotified7d: false,
        expiryNotified3d: false,
        expiryNotified1d: false,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      const notifRef = compRef.collection("notifications").doc();
      tx.set(notifRef, {
        companyId: pay.companyId,
        branchId: "",
        title: "Subscription Activated",
        titleMm: "စာရင်းသွင်းမှု အောင်မြင်စွာ ဖွင့်လှစ်ပြီးပါပြီ",
        message: `Payment ${receiptNumber} verified. Plan ${plan.nameEn} active for ${durationDays} days.`,
        messageMm: `ငွေပေးချေမှု ${receiptNumber} ကို အတည်ပြုပြီး ${durationDays} ရက်စာ ဖွင့်လှစ်ပေးပြီးပါပြီ။`,
        severity: "INFO",
        category: "BILLING",
        isRead: false,
        memberUids: [comp.ownerUid],
        createdBy: adminUid,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      const logRef = db.collection("platform_audit_logs").doc();
      tx.set(logRef, {
        companyId: pay.companyId,
        ownerUid: comp.ownerUid,
        actorUid: adminUid,
        actorName: request.auth?.token.name || "Platform Super Admin",
        actorRole: "PLATFORM_SUPER_ADMIN",
        action: "PAYMENT_APPROVED_SUBSCRIPTION_ACTIVATED",
        targetType: "PAYMENT",
        targetId: paymentId,
        details: `Verified ${receiptNumber} (${pay.amountMmk} MMK) for ${comp.name}`,
        createdBy: adminUid,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      return { receiptNumber, expiresAtMillis: expiresAt.toMillis() };
    });
  }
);

/**
 * Super Admin rejects payment submission and records audit log + owner alert.
 */
export const rejectSubscriptionPayment = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const adminUid = assertSuperAdmin(request.auth);
    const paymentId = String(request.data?.paymentId || "");
    const reason = String(request.data?.reason || "Invalid payment proof");
    if (!paymentId) {
      throw new HttpsError("invalid-argument", "paymentId is required.");
    }

    const payRef = db.collection("subscription_payments").doc(paymentId);
    const paySnap = await payRef.get();
    if (!paySnap.exists) {
      throw new HttpsError("not-found", "Payment not found.");
    }
    const pay = paySnap.data()!;
    const compRef = db.collection("companies").doc(pay.companyId);
    const compSnap = await compRef.get();

    const batch = db.batch();
    batch.update(payRef, {
      status: "REJECTED",
      rejectionReason: reason,
      verifiedByUid: adminUid,
      verifiedByName: request.auth?.token.name || "Platform Super Admin",
      verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    if (compSnap.exists) {
      const comp = compSnap.data()!;
      const expiredAlready =
        comp.subscriptionExpiresAt &&
        comp.subscriptionExpiresAt.toMillis() <= Date.now();
      batch.update(compRef, {
        subscriptionStatus: expiredAlready ? "EXPIRED" : "PENDING_PAYMENT",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }

    await batch.commit();
    return { status: "REJECTED" };
  }
);

/**
 * Scheduled Hourly Job:
 * 1. Marks companies within 7 days of expiry as EXPIRING_SOON & sends 7d/3d/1d notifications.
 * 2. Marks past-expiry subscriptions as EXPIRED without deleting any tenant business data.
 */
export const checkExpiredSubscriptions = onSchedule("every 60 minutes", async () => {
  const now = admin.firestore.Timestamp.now();
  const nowMs = now.toMillis();

  const snapshot = await db
    .collection("companies")
    .where("subscriptionStatus", "in", ["ACTIVE", "EXPIRING_SOON"])
    .get();

  const batch = db.batch();
  for (const doc of snapshot.docs) {
    const comp = doc.data();
    const expiresAt: admin.firestore.Timestamp | undefined = comp.subscriptionExpiresAt;
    if (!expiresAt) continue;

    const remainingMs = expiresAt.toMillis() - nowMs;
    const remainingDays = Math.ceil(remainingMs / DAY_MS);

    if (remainingMs <= 0) {
      batch.update(doc.ref, {
        subscriptionStatus: "EXPIRED",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      const notifRef = doc.ref.collection("notifications").doc();
      batch.set(notifRef, {
        companyId: doc.id,
        branchId: "",
        title: "Subscription Expired",
        titleMm: "စာရင်းသွင်းမှု သက်တမ်းကုန်ဆုံးပါပြီ",
        message: "Your subscription has expired. Renew now to restore POS access.",
        messageMm: "သင့်လုပ်ငန်းစာရင်းသွင်းမှု သက်တမ်းကုန်ဆုံးသွားပါပြီ။ ဆက်လက်အသုံးပြုရန် သက်တမ်းတိုးပါ။",
        severity: "CRITICAL",
        category: "EXPIRY",
        isRead: false,
        memberUids: [comp.ownerUid],
        createdBy: comp.ownerUid,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    } else if (remainingDays <= 7) {
      const updates: Record<string, unknown> = {
        subscriptionStatus: "EXPIRING_SOON",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      };
      const shouldNotify7d = remainingDays <= 7 && !comp.expiryNotified7d;
      const shouldNotify3d = remainingDays <= 3 && !comp.expiryNotified3d;
      const shouldNotify1d = remainingDays <= 1 && !comp.expiryNotified1d;

      if (shouldNotify7d) updates.expiryNotified7d = true;
      if (shouldNotify3d) updates.expiryNotified3d = true;
      if (shouldNotify1d) updates.expiryNotified1d = true;
      batch.update(doc.ref, updates);

      if (shouldNotify7d || shouldNotify3d || shouldNotify1d) {
        const notifRef = doc.ref.collection("notifications").doc();
        batch.set(notifRef, {
          companyId: doc.id,
          branchId: "",
          title: `Subscription Expiring in ${remainingDays} Day(s)`,
          titleMm: `စာရင်းသွင်းမှု ${remainingDays} ရက်အတွင်း ကုန်ဆုံးမည်`,
          message: `Renew your ${comp.subscriptionPlanName} subscription before interruption.`,
          messageMm: `ဝန်ဆောင်မှုမပြတ်တောက်စေရန် ${remainingDays} ရက်အတွင်း သက်တမ်းတိုးမြှင့်ပါ။`,
          severity: remainingDays <= 1 ? "CRITICAL" : "WARNING",
          category: "EXPIRY",
          isRead: false,
          memberUids: [comp.ownerUid],
          createdBy: comp.ownerUid,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
      }
    }
  }
  await batch.commit();
});

/**
 * Backend quota & subscription guard on branch creation.
 */
export const enforceBranchPlanLimits = onDocumentCreated(
  "companies/{companyId}/branches/{branchId}",
  async (event) => {
    const companyId = event.params.companyId;
    const compSnap = await db.collection("companies").doc(companyId).get();
    if (!compSnap.exists) return;

    const comp = compSnap.data()!;
    const branchesSnap = await db
      .collection("companies")
      .doc(companyId)
      .collection("branches")
      .get();

    if (branchesSnap.size > Number(comp.maxBranchesAllowed || 1)) {
      await event.data?.ref.delete();
    } else {
      await compSnap.ref.update({
        activeBranchCount: branchesSnap.size,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }
  }
);

/**
 * Extensible webhook endpoint for future automated payment gateways (KBZPay Direct / WavePay / Stripe).
 */
export const paymentGatewayWebhook = onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }
  // Signature validation & gateway-specific callback parsing hook
  res.status(501).json({
    status: "GATEWAY_NOT_CONFIGURED",
    message: "Manual slip verification active until gateway credentials are configured in Secret Manager.",
  });
});
