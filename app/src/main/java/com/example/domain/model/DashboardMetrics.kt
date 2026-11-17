package com.example.domain.model

data class DashboardMetrics(
    val todaySalesMmk: Long = 0L,
    val todayProfitMmk: Long = 0L,
    val totalExpensesMmk: Long = 0L,
    val netProfitMmk: Long = 0L,
    val totalTransactions: Long = 0L,
    val totalProducts: Long = 0L,
    val totalCustomers: Long = 0L,
    val totalOutstandingDebtMmk: Long = 0L,
    val totalInventoryValueMmk: Long = 0L,
    val activeBranchCount: Int = 0,
    val totalBranchCount: Int = 0,
    val bestBranch: Branch? = null,
    val lowestBranch: Branch? = null,
    val rankedBranches: List<Branch> = emptyList(),
)

object DashboardCalculator {
    fun computeHeadOfficeMetrics(branches: List<Branch>): DashboardMetrics {
        if (branches.isEmpty()) return DashboardMetrics()
        val ranked = branches.sortedByDescending { it.todaySalesMmk }
        val activeBranches = branches.filter { it.isActive }
        val todaySales = branches.sumOf { it.todaySalesMmk }
        val todayProfit = branches.sumOf { it.todayProfitMmk }
        val totalExpenses = branches.sumOf { it.todayExpensesMmk }
        return DashboardMetrics(
            todaySalesMmk = todaySales,
            todayProfitMmk = todayProfit,
            totalExpensesMmk = totalExpenses,
            netProfitMmk = todayProfit - totalExpenses,
            totalTransactions = branches.sumOf { it.todayTransactions },
            totalProducts = branches.sumOf { it.productCount },
            totalCustomers = branches.sumOf { it.customerCount },
            totalOutstandingDebtMmk = branches.sumOf { it.outstandingDebtMmk },
            totalInventoryValueMmk = branches.sumOf { it.inventoryValueMmk },
            activeBranchCount = activeBranches.size,
            totalBranchCount = branches.size,
            bestBranch = ranked.firstOrNull(),
            lowestBranch = if (ranked.size > 1) ranked.lastOrNull() else ranked.firstOrNull(),
            rankedBranches = ranked,
        )
    }

    fun computeBranchMetrics(branch: Branch?): DashboardMetrics {
        if (branch == null) return DashboardMetrics()
        return DashboardMetrics(
            todaySalesMmk = branch.todaySalesMmk,
            todayProfitMmk = branch.todayProfitMmk,
            totalExpensesMmk = branch.todayExpensesMmk,
            netProfitMmk = branch.todayProfitMmk - branch.todayExpensesMmk,
            totalTransactions = branch.todayTransactions,
            totalProducts = branch.productCount,
            totalCustomers = branch.customerCount,
            totalOutstandingDebtMmk = branch.outstandingDebtMmk,
            totalInventoryValueMmk = branch.inventoryValueMmk,
            activeBranchCount = if (branch.isActive) 1 else 0,
            totalBranchCount = 1,
            bestBranch = branch,
            lowestBranch = branch,
            rankedBranches = listOf(branch),
        )
    }
}
