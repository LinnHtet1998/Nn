const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const SUPER_ADMIN_UID = "super_admin_001";
const SUPER_ADMIN_EMAIL = "storelinnhtet@gmail.com";
const BIZ_OWNER_UID = "biz_owner_002";
const BIZ_OWNER_EMAIL = "owner.yangon@minimart.mm";
const CASHIER_UID = "cashier_003";
const CASHIER_EMAIL = "cashier@minimart.mm";
const PENDING_UID = "pending_004";
const PENDING_EMAIL = "pending@minimart.mm";
const STRANGER_UID = "stranger_999";
const STRANGER_EMAIL = "stranger@gmail.com";

const COMPANY_A_ID = "comp_yangon_a";
const COMPANY_B_ID = "comp_mdy_b";
const HQ_BRANCH_ID = "branch_hq_a";
const BRANCH_2_ID = "branch_2_a";

function emailKey(email) {
  return email.trim().toLowerCase().replace(/[^a-zA-Z0-9_-]/g, "_");
}

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

async function seedSaasWorld({ companyAStatus = "ACTIVE", expiresOffsetMs = 30 * 86400000 } = {}) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = new Date(Date.now() - 5000);
    const expiresAt = new Date(Date.now() + expiresOffsetMs);

    await db.collection("subscription_plans").doc("plan_1m").set({
      nameEn: "Starter 1 Month",
      nameMm: "၁ လ အစီအစဉ်",
      durationMonths: 1,
      durationDays: 30,
      priceMmk: 35000,
      discountPercent: 0,
      promoPriceMmk: 35000,
      maxBranches: 3,
      maxEmployees: 15,
      enabledFeatures: ["MULTI_BRANCH", "OFFLINE_POS"],
      isActive: true,
      sortOrder: 1,
      createdBy: SUPER_ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("payment_methods").doc("kbzpay").set({
      nameEn: "KBZPay",
      nameMm: "KBZPay",
      providerType: "MANUAL_TRANSFER",
      accountTitle: "Mini Mart Pro Platform",
      accountNumber: "09420009999",
      instructionsEn: "Transfer MMK and attach slip",
      instructionsMm: "ငွေလွှဲပြီး စလစ်ပုံတင်ပါ",
      isActive: true,
      createdBy: SUPER_ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("companies").doc(COMPANY_A_ID).set({
      ownerUid: BIZ_OWNER_UID,
      ownerName: "U Aung Kyaw",
      ownerEmail: BIZ_OWNER_EMAIL,
      name: "Yangon Fresh Mart",
      nameMm: "ရန်ကုန် ဖရက်ရှ် မတ်",
      phone: "09420001122",
      address: "Yangon",
      currency: "MMK",
      defaultLanguage: "my",
      taxRatePercent: 5,
      allowNegativeStock: false,
      offlineLimitMmk: 500000,
      subscriptionPlanId: "plan_1m",
      subscriptionPlanName: "Starter 1 Month",
      subscriptionStatus: companyAStatus,
      subscriptionStartAt: now,
      subscriptionExpiresAt: expiresAt,
      maxBranchesAllowed: 3,
      maxEmployeesAllowed: 15,
      enabledFeatures: ["MULTI_BRANCH", "OFFLINE_POS"],
      activeBranchCount: 2,
      activeEmployeeCount: 2,
      createdBy: BIZ_OWNER_UID,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("companies").doc(COMPANY_A_ID).collection("members").doc(BIZ_OWNER_UID).set({
      companyId: COMPANY_A_ID,
      ownerUid: BIZ_OWNER_UID,
      userId: BIZ_OWNER_UID,
      employeeId: "EMP-000",
      email: BIZ_OWNER_EMAIL,
      displayName: "U Aung Kyaw",
      role: "SUPER_ADMIN",
      status: "APPROVED",
      allBranchesAccess: true,
      assignedBranchIds: [],
      adminUids: [BIZ_OWNER_UID],
      createdBy: BIZ_OWNER_UID,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("companies").doc(COMPANY_A_ID).collection("members").doc(CASHIER_UID).set({
      companyId: COMPANY_A_ID,
      ownerUid: BIZ_OWNER_UID,
      userId: CASHIER_UID,
      employeeId: "EMP-002",
      email: CASHIER_EMAIL,
      displayName: "Ma Hlaing",
      role: "CASHIER",
      status: "APPROVED",
      allBranchesAccess: false,
      assignedBranchIds: [HQ_BRANCH_ID],
      adminUids: [BIZ_OWNER_UID],
      createdBy: BIZ_OWNER_UID,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("account_directory").doc(emailKey(PENDING_EMAIL)).set({
      companyId: COMPANY_A_ID,
      ownerUid: BIZ_OWNER_UID,
      employeeId: "EMP-003",
      email: PENDING_EMAIL,
      displayName: "Pending Staff",
      phone: "09420006666",
      role: "CASHIER",
      status: "PENDING",
      allBranchesAccess: false,
      assignedBranchIds: [HQ_BRANCH_ID],
      createdBy: BIZ_OWNER_UID,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("companies").doc(COMPANY_A_ID).collection("branches").doc(HQ_BRANCH_ID).set({
      companyId: COMPANY_A_ID,
      ownerUid: BIZ_OWNER_UID,
      code: "HQ-001",
      name: "Yangon HQ",
      nameMm: "ရန်ကုန် ရုံးချုပ်",
      address: "Yangon",
      phone: "09420001122",
      isHeadOffice: true,
      isActive: true,
      memberUids: [BIZ_OWNER_UID, CASHIER_UID],
      todaySalesMmk: 1000000,
      todayProfitMmk: 250000,
      todayExpensesMmk: 50000,
      todayTransactions: 20,
      productCount: 100,
      customerCount: 40,
      outstandingDebtMmk: 20000,
      inventoryValueMmk: 5000000,
      createdBy: BIZ_OWNER_UID,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("companies").doc(COMPANY_A_ID).collection("branches").doc(BRANCH_2_ID).set({
      companyId: COMPANY_A_ID,
      ownerUid: BIZ_OWNER_UID,
      code: "YGN-002",
      name: "Hledan Branch",
      nameMm: "လှည်းတန်းဆိုင်ခွဲ",
      address: "Hledan",
      phone: "09420002233",
      isHeadOffice: false,
      isActive: true,
      memberUids: [BIZ_OWNER_UID],
      todaySalesMmk: 500000,
      todayProfitMmk: 120000,
      todayExpensesMmk: 20000,
      todayTransactions: 10,
      productCount: 80,
      customerCount: 20,
      outstandingDebtMmk: 0,
      inventoryValueMmk: 3000000,
      createdBy: BIZ_OWNER_UID,
      createdAt: now,
      updatedAt: now,
    });

    // Separate tenant Company B
    await db.collection("companies").doc(COMPANY_B_ID).set({
      ownerUid: STRANGER_UID,
      ownerName: "Mandalay Owner",
      ownerEmail: STRANGER_EMAIL,
      name: "Mandalay Mart B",
      nameMm: "မန္တလေး မတ် B",
      phone: "099000111",
      address: "Mandalay",
      currency: "MMK",
      defaultLanguage: "my",
      taxRatePercent: 5,
      allowNegativeStock: false,
      offlineLimitMmk: 200000,
      subscriptionPlanId: "plan_1m",
      subscriptionPlanName: "Starter 1 Month",
      subscriptionStatus: "ACTIVE",
      subscriptionStartAt: now,
      subscriptionExpiresAt: expiresAt,
      maxBranchesAllowed: 3,
      maxEmployeesAllowed: 15,
      enabledFeatures: ["MULTI_BRANCH"],
      activeBranchCount: 1,
      activeEmployeeCount: 1,
      createdBy: STRANGER_UID,
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Only Platform Super Admin can create/edit/delete subscription plans & payment methods", async () => {
  await seedSaasWorld();
  const now = new Date(Date.now() - 2000);
  const superDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, { email: SUPER_ADMIN_EMAIL, email_verified: true }).firestore();
  const ownerDb = testEnv.authenticatedContext(BIZ_OWNER_UID, { email: BIZ_OWNER_EMAIL, email_verified: true }).firestore();

  // Business owner can read plans & payment methods, but CANNOT mutate price or create plans
  await assertSucceeds(ownerDb.collection("subscription_plans").doc("plan_1m").get());
  await assertSucceeds(ownerDb.collection("payment_methods").doc("kbzpay").get());
  await assertFails(ownerDb.collection("subscription_plans").doc("plan_1m").update({ priceMmk: 1, updatedAt: now }));
  await assertFails(ownerDb.collection("payment_methods").doc("kbzpay").update({ accountNumber: "099999", updatedAt: now }));

  // Super Admin CAN create & edit plans
  await assertSucceeds(superDb.collection("subscription_plans").doc("plan_1y").set({
    nameEn: "Enterprise 1 Year",
    nameMm: "၁ နှစ် အထူးအစီအစဉ်",
    durationMonths: 12,
    durationDays: 365,
    priceMmk: 360000,
    discountPercent: 15,
    promoPriceMmk: 306000,
    promoLabel: "Save 15%",
    maxBranches: 50,
    maxEmployees: 200,
    enabledFeatures: ["MULTI_BRANCH", "OFFLINE_POS", "ANALYTICS", "PAYROLL"],
    isActive: true,
    sortOrder: 3,
    createdBy: SUPER_ADMIN_UID,
    createdAt: now,
    updatedAt: now,
  }));
});

test("Business Owner CANNOT self-activate subscription, change expiry date, or self-verify payments", async () => {
  await seedSaasWorld({ companyAStatus: "PENDING_PAYMENT", expiresOffsetMs: -86400000 });
  const now = new Date(Date.now() - 2000);
  const future = new Date(Date.now() + 365 * 86400000);
  const ownerDb = testEnv.authenticatedContext(BIZ_OWNER_UID, { email: BIZ_OWNER_EMAIL, email_verified: true }).firestore();
  const superDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, { email: SUPER_ADMIN_EMAIL, email_verified: true }).firestore();

  // Business owner cannot force ACTIVE status or alter subscriptionExpiresAt
  await assertFails(ownerDb.collection("companies").doc(COMPANY_A_ID).update({
    subscriptionStatus: "ACTIVE",
    subscriptionExpiresAt: future,
    updatedAt: now,
  }));

  // Business owner cannot create a pre-VERIFIED payment
  await assertFails(ownerDb.collection("subscription_payments").doc("pay_spoof").set({
    companyId: COMPANY_A_ID,
    companyName: "Yangon Fresh Mart",
    ownerUid: BIZ_OWNER_UID,
    ownerName: "U Aung Kyaw",
    ownerEmail: BIZ_OWNER_EMAIL,
    planId: "plan_1m",
    planNameEn: "Starter 1 Month",
    planNameMm: "၁ လ အစီအစဉ်",
    durationMonths: 1,
    durationDays: 30,
    amountMmk: 35000,
    paymentMethodId: "kbzpay",
    paymentMethodName: "KBZPay",
    transactionRef: "TXN-999",
    paymentDateText: "2026-11-16",
    status: "VERIFIED",
    receiptNumber: "INV-SPOOF",
    createdBy: BIZ_OWNER_UID,
    createdAt: now,
    updatedAt: now,
  }));

  // Business owner CAN submit PENDING_VERIFICATION payment proof
  await assertSucceeds(ownerDb.collection("subscription_payments").doc("pay_valid_01").set({
    companyId: COMPANY_A_ID,
    companyName: "Yangon Fresh Mart",
    ownerUid: BIZ_OWNER_UID,
    ownerName: "U Aung Kyaw",
    ownerEmail: BIZ_OWNER_EMAIL,
    planId: "plan_1m",
    planNameEn: "Starter 1 Month",
    planNameMm: "၁ လ အစီအစဉ်",
    durationMonths: 1,
    durationDays: 30,
    amountMmk: 35000,
    paymentMethodId: "kbzpay",
    paymentMethodName: "KBZPay",
    transactionRef: "KBZ-882910",
    paymentDateText: "2026-11-16",
    status: "PENDING_VERIFICATION",
    receiptNumber: "",
    createdBy: BIZ_OWNER_UID,
    createdAt: now,
    updatedAt: now,
  }));

  // Owner cannot update payment to VERIFIED; ONLY Super Admin can verify and activate company!
  await assertFails(ownerDb.collection("subscription_payments").doc("pay_valid_01").update({
    status: "VERIFIED",
    receiptNumber: "INV-01",
    updatedAt: now,
  }));

  await assertSucceeds(superDb.collection("subscription_payments").doc("pay_valid_01").update({
    status: "VERIFIED",
    receiptNumber: "INV-20261116-0001",
    verifiedByUid: SUPER_ADMIN_UID,
    verifiedByName: "Platform Super Admin",
    verifiedAt: now,
    grantedStartAt: now,
    grantedExpiresAt: future,
    updatedAt: now,
  }));
});

test("Server-time expiration blocks branch/POS access even if status field is still ACTIVE", async () => {
  // Simulate subscription whose expiry timestamp ALREADY passed in server time (cannot be spoofed by device clock!)
  await seedSaasWorld({ companyAStatus: "ACTIVE", expiresOffsetMs: -60000 });
  const ownerDb = testEnv.authenticatedContext(BIZ_OWNER_UID, { email: BIZ_OWNER_EMAIL, email_verified: true }).firestore();
  const cashierDb = testEnv.authenticatedContext(CASHIER_UID, { email: CASHIER_EMAIL, email_verified: true }).firestore();

  // Operational branch data is strictly BLOCKED when expired in server time
  await assertFails(ownerDb.collection("companies").doc(COMPANY_A_ID).collection("branches").doc(HQ_BRANCH_ID).get());
  await assertFails(cashierDb.collection("companies").doc(COMPANY_A_ID).collection("branches").doc(HQ_BRANCH_ID).get());

  // Business Owner can STILL read their own company root doc to view EXPIRED status & submit renewal!
  await assertSucceeds(ownerDb.collection("companies").doc(COMPANY_A_ID).get());
  // Employee CANNOT read expired company doc!
  await assertFails(cashierDb.collection("companies").doc(COMPANY_A_ID).get());
});

test("Strict multi-tenant isolation & owner-controlled employee authorization", async () => {
  await seedSaasWorld({ companyAStatus: "ACTIVE", expiresOffsetMs: 30 * 86400000 });
  const ownerDb = testEnv.authenticatedContext(BIZ_OWNER_UID, { email: BIZ_OWNER_EMAIL, email_verified: true }).firestore();
  const cashierDb = testEnv.authenticatedContext(CASHIER_UID, { email: CASHIER_EMAIL, email_verified: true }).firestore();
  const pendingDb = testEnv.authenticatedContext(PENDING_UID, { email: PENDING_EMAIL, email_verified: true }).firestore();

  // Tenant isolation: Company A owner cannot read Company B
  await assertFails(ownerDb.collection("companies").doc(COMPANY_B_ID).get());

  // Cashier accesses HQ branch only, denied on Branch 2
  await assertSucceeds(cashierDb.collection("companies").doc(COMPANY_A_ID).collection("branches").doc(HQ_BRANCH_ID).get());
  await assertFails(cashierDb.collection("companies").doc(COMPANY_A_ID).collection("branches").doc(BRANCH_2_ID).get());

  // Pending employee denied on company and branches
  await assertSucceeds(pendingDb.collection("account_directory").doc(emailKey(PENDING_EMAIL)).get());
  await assertFails(pendingDb.collection("companies").doc(COMPANY_A_ID).get());
  await assertFails(pendingDb.collection("companies").doc(COMPANY_A_ID).collection("branches").doc(HQ_BRANCH_ID).get());
});
