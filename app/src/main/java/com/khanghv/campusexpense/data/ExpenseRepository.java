package com.khanghv.campusexpense.data;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.khanghv.campusexpense.data.database.AppDatabase;
import com.khanghv.campusexpense.data.database.BudgetDao;
import com.khanghv.campusexpense.data.database.CategoryDao;
import com.khanghv.campusexpense.data.database.ExpenseDao;
import com.khanghv.campusexpense.data.database.UserDao;
import com.khanghv.campusexpense.data.model.Budget;
import com.khanghv.campusexpense.data.model.Category;
import com.khanghv.campusexpense.data.model.User;
import com.khanghv.campusexpense.data.model.Expense;  // Adjust package nếu cần

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ExpenseRepository {
    private ExpenseDao expenseDao;
    private BudgetDao budgetDao;
    private UserDao userDao;
    private CategoryDao categoryDao;

    public ExpenseRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        expenseDao = db.expenseDao();
        budgetDao = db.budgetDao();
        userDao = db.userDao();
        categoryDao = db.categoryDao();
    }

    // User methods (cho Greeting)
    public LiveData<User> getUserById(int userId) {
        return userDao.getUserById(userId);
    }

    // Expense methods (cho total/count tháng)
    public LiveData<Double> getTotalSpentForMonth(String monthYear, int userId) {
        long[] dateRange = getMonthDateRange(monthYear);
        return expenseDao.getTotalSpentForMonth(dateRange[0], dateRange[1], userId);
    }

    public LiveData<Integer> getTransactionCountForMonth(String monthYear, int userId) {
        long[] dateRange = getMonthDateRange(monthYear);
        return expenseDao.getTransactionCountForMonth(dateRange[0], dateRange[1], userId);
    }

    private long[] getMonthDateRange(String monthYear) {
        String[] parts = monthYear.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]) - 1; // Calendar.MONTH là 0-based

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startDate = cal.getTimeInMillis();

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        long endDate = cal.getTimeInMillis();

        return new long[]{startDate, endDate};
    }

    // Budget methods
    public LiveData<Double> getCurrentBudget(int userId) {
        LiveData<List<Budget>> budgetsLiveData = budgetDao.getAllBudgetsByUserLiveData(userId);
        return Transformations.map(budgetsLiveData, budgets -> {
            if (budgets == null || budgets.isEmpty()) {
                return 0.0;
            }
            double total = 0.0;
            for (Budget budget : budgets) {
                total += budget.getAmount();
            }
            return total;
        });
    }

    // Async insert expense
    public void insertExpense(Expense expense) {
        new Thread(() -> expenseDao.insert(expense)).start();
    }

    // Budget breakdown by category
    public static class BudgetBreakdownItem {
        public int categoryId;
        public String categoryName;
        public double budgetAmount;
        public double spentAmount;
        public int percentage;

        public BudgetBreakdownItem(int categoryId, String categoryName, double budgetAmount, double spentAmount) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.budgetAmount = budgetAmount;
            this.spentAmount = spentAmount;
            this.percentage = budgetAmount > 0 ? (int) ((spentAmount / budgetAmount) * 100) : 0;
        }
    }

    public LiveData<List<BudgetBreakdownItem>> getBudgetBreakdown(String monthYear, int userId) {
        long[] dateRange = getMonthDateRange(monthYear);
        LiveData<List<Budget>> budgetsLiveData = budgetDao.getAllBudgetsByUserLiveData(userId);
        
        return Transformations.switchMap(budgetsLiveData, budgets -> {
            MutableLiveData<List<BudgetBreakdownItem>> result = new MutableLiveData<>();
            
            new Thread(() -> {
                List<BudgetBreakdownItem> breakdownList = new ArrayList<>();
                if (budgets == null || budgets.isEmpty()) {
                    result.postValue(breakdownList);
                    return;
                }
                
                // Lấy tất cả categories một lần
                List<Category> allCategories = categoryDao.getAllByUser(userId);
                java.util.Map<Integer, String> categoryMap = new java.util.HashMap<>();
                for (Category cat : allCategories) {
                    categoryMap.put(cat.getId(), cat.getName());
                }
                
                // Tạo breakdown items
                for (Budget budget : budgets) {
                    String categoryName = categoryMap.get(budget.getCategoryId());
                    if (categoryName == null) continue;
                    
                    Double totalSpent = expenseDao.getTotalExpensesByCategoryAndDateRange(
                        userId, budget.getCategoryId(), dateRange[0], dateRange[1]);
                    double spent = totalSpent != null ? totalSpent : 0.0;
                    
                    breakdownList.add(new BudgetBreakdownItem(
                        budget.getCategoryId(),
                        categoryName,
                        budget.getAmount(),
                        spent
                    ));
                }
                
                result.postValue(breakdownList);
            }).start();
            
            return result;
        });
    }
}
