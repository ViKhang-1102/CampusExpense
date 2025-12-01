package com.khanghv.campusexpense.ui.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat; // Đảm bảo đã import
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.khanghv.campusexpense.R;
import com.khanghv.campusexpense.data.ExpenseRepository;
import com.khanghv.campusexpense.data.model.User;
import com.khanghv.campusexpense.ui.home.BudgetBreakdownAdapter;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private TextView tvGreeting, tvTotalSpent, tvTransactionCount, tvAvgPerDay, tvBudget,
            tvTotalBudget, tvSpent, tvRemaining;
    private RecyclerView recyclerViewBreakdown;
    private BudgetBreakdownAdapter breakdownAdapter;
    private ExpenseRepository repository;
    private int currentUserId;
    private String currentMonthYear;
    private final DecimalFormat currencyFormat = new DecimalFormat("#,###");

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Init views
        tvGreeting = view.findViewById(R.id.tvGreeting);
        tvTotalSpent = view.findViewById(R.id.tvTotalSpent);
        tvTransactionCount = view.findViewById(R.id.tvTransactionCount);
        tvAvgPerDay = view.findViewById(R.id.tvAvgPerDay);
        tvBudget = view.findViewById(R.id.tvBudget);
        recyclerViewBreakdown = view.findViewById(R.id.recyclerViewBreakdown);
        tvTotalBudget = view.findViewById(R.id.tvTotalBudget);
        tvSpent = view.findViewById(R.id.tvSpent);
        tvRemaining = view.findViewById(R.id.tvRemaining);

        // Setup breakdown RecyclerView
        breakdownAdapter = new BudgetBreakdownAdapter(new ArrayList<>());
        recyclerViewBreakdown.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewBreakdown.setAdapter(breakdownAdapter);

        // Init repo
        repository = new ExpenseRepository((android.app.Application) requireContext().getApplicationContext());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Tải lại toàn bộ dữ liệu để đảm bảo tính nhất quán
        refreshData();
    }

    private void refreshData() {
        // Lấy thông tin mới nhất mỗi khi refresh
        currentUserId = getCurrentUserId();
        currentMonthYear = getCurrentMonthYear();

        // Kiểm tra userId hợp lệ
        if (currentUserId == -1) {
            tvGreeting.setText(getString(R.string.greeting, "User"));
            return;
        }

        // Load user cho greeting
        repository.getUserById(currentUserId).observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                tvGreeting.setText(String.format(getString(R.string.greeting), user.getUsername()));
            } else {
                tvGreeting.setText(getString(R.string.greeting, "User")); // Giá trị mặc định
            }
        });

        // Load dữ liệu tài chính
        loadDataForMonth(currentMonthYear, currentUserId);
    }


    private void loadDataForMonth(String monthYear, int userId) {
        final Double[] totalSpentValue = {0.0};
        final Double[] budgetValue = {0.0};

        // Observe total spent
        repository.getTotalSpentForMonth(monthYear, userId).observe(getViewLifecycleOwner(), totalSpent -> {
            if (totalSpent == null) totalSpent = 0.0;
            totalSpentValue[0] = totalSpent;
            String currencySymbol = getString(R.string.currency_symbol);
            String formatted = currencyFormat.format(totalSpent) + " " + currencySymbol;
            tvTotalSpent.setText(formatted);
            tvSpent.setText(formatted);
            // Cập nhật với budget hiện tại
            updateSummaryAndBreakdown(budgetValue[0], totalSpent);
            updateAvgPerDay(totalSpent);
        });

        // Observe count
        repository.getTransactionCountForMonth(monthYear, userId).observe(getViewLifecycleOwner(), count -> {
            if (count == null) count = 0;
            tvTransactionCount.setText(String.valueOf(count));
        });

        // Observe budget - đảm bảo observer được add đúng cách
        repository.getCurrentBudget(userId).observe(getViewLifecycleOwner(), totalBudget -> {
            if (totalBudget == null) {
                totalBudget = 0.0;
            }
            budgetValue[0] = totalBudget;
            String currencySymbol = getString(R.string.currency_symbol);
            String formatted = currencyFormat.format(totalBudget) + " " + currencySymbol;
            tvBudget.setText(formatted);
            tvTotalBudget.setText(formatted);
            // Cập nhật với spent hiện tại
            updateSummaryAndBreakdown(totalBudget, totalSpentValue[0]);
        });

        // Observe budget breakdown
        repository.getBudgetBreakdown(monthYear, userId).observe(getViewLifecycleOwner(), breakdownList -> {
            if (breakdownList != null) {
                breakdownAdapter.updateBreakdownList(breakdownList);
            }
        });
    }

    private void updateSummaryAndBreakdown(double budget, double spent) {
        double remaining = budget - spent;
        String currencySymbol = getString(R.string.currency_symbol);
        String remainingText = (remaining < 0 ? "-" : "") + currencyFormat.format(Math.abs(remaining)) + " " + currencySymbol;
        tvRemaining.setText(remainingText);

        int colorRes = remaining < 0 ? android.R.color.holo_red_dark : android.R.color.holo_green_dark;
        tvRemaining.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
    }

    private void updateAvgPerDay(double totalSpent) {
        int daysInMonth = getDaysInMonth(currentMonthYear);
        if (daysInMonth > 0) {
            double avgDaily = totalSpent / daysInMonth;
            String currencySymbol = getString(R.string.currency_symbol);
            tvAvgPerDay.setText(currencyFormat.format(avgDaily) + " " + currencySymbol);
        }
    }

    private String getCurrentMonthYear() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        return sdf.format(cal.getTime());
    }

    private int getDaysInMonth(String monthYear) {
        String[] parts = monthYear.split("-");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, Integer.parseInt(parts[0]));
        cal.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private int getCurrentUserId() {
        SharedPreferences prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        return prefs.getInt("userId", -1);
    }
}
