package com.khanghv.campusexpense.ui.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.khanghv.campusexpense.R;
import com.khanghv.campusexpense.data.database.AppDatabase;
import com.khanghv.campusexpense.data.database.BudgetDao;
import com.khanghv.campusexpense.data.database.CategoryDao;
import com.khanghv.campusexpense.data.model.Budget;
import com.khanghv.campusexpense.data.model.Category;
import com.khanghv.campusexpense.ui.budget.BudgetRecyclerAdapter;
import com.khanghv.campusexpense.util.CurrencyManager;

import java.util.ArrayList;
import java.util.List;

public class BudgetFragment extends Fragment {
    private RecyclerView recyclerView;
    private FloatingActionButton fabAdd;
    private BudgetRecyclerAdapter adapter;
    private List<Budget> budgetList;
    private List<Category> categoryList;
    private List<String> categoryNames;
    private BudgetDao budgetDao;
    private CategoryDao categoryDao;
    private TextView emptyView;
    private SharedPreferences sharedPreferences;
    private int currentUserId;


    @Override
    @Nullable
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_budget, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        fabAdd = view.findViewById(R.id.fabAdd);
        emptyView = view.findViewById(R.id.emptyView);
        sharedPreferences = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        currentUserId = sharedPreferences.getInt("userId", -1);
        AppDatabase db = AppDatabase.getInstance(requireContext());
        budgetDao = db.budgetDao();
        categoryDao = db.categoryDao();
        budgetList = new ArrayList<>();
        categoryList = new ArrayList<>();
        categoryNames = new ArrayList<>();
        adapter = new BudgetRecyclerAdapter(budgetList, categoryNames, this::showEditDialog, this::showDeleteDialog);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        fabAdd.setOnClickListener(v -> showAddBudgetDialog());
        CurrencyManager.refreshRateIfNeeded(requireContext(), false, null);
        refreshBudgetList();
        return view;
    }

    private void refreshBudgetList() {
        budgetList.clear();
        budgetList.addAll(budgetDao.getAllBudgetsByUser(currentUserId));
        categoryNames.clear();
        for (Budget budget : budgetList) {
            Category category = categoryDao.getById(budget.getCategoryId());
            categoryNames.add(category == null ? "Unknown Category" : category.getName());
        }
        adapter.notifyDataSetChanged();
        if (budgetList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showDeleteDialog(Budget budget) {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_budget))
                .setMessage(getString(R.string.confirm_delete_budget))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    budgetDao.delete(budget);
                    refreshBudgetList();
                    Toast.makeText(requireContext(), getString(R.string.budget_deleted), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showAddBudgetDialog() {
        categoryList.clear();
        categoryList.addAll(categoryDao.getAllByUser(currentUserId));

        if (categoryList.isEmpty()) {
            Toast.makeText(requireContext(), "Please add categories first", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_budget, null);
        Spinner categorySpinner = dialogView.findViewById(R.id.categorySpinner);
        TextInputEditText amountInput = dialogView.findViewById(R.id.amountInput);
        TextInputLayout amountLayout = dialogView.findViewById(R.id.amountLayout);
        Spinner periodSpinner = dialogView.findViewById(R.id.periodSpinner);
        Button saveButton = dialogView.findViewById(R.id.saveButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);

        if (amountLayout != null) {
            String symbol = CurrencyManager.getCurrencySymbol(requireContext());
            amountLayout.setHint(getString(R.string.amount_with_currency, symbol));
        }

        List<String> categoryNameList = new ArrayList<>();
        for (Category cat : categoryList) {
            categoryNameList.add(cat.getName());
        }

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, categoryNameList);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);

        String[] periods = {"Monthly", "Weekly"};
        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, periods);
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        periodSpinner.setAdapter(periodAdapter);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        saveButton.setOnClickListener(v -> {
            int categoryPosition = categorySpinner.getSelectedItemPosition();
            String amountStr = amountInput.getText().toString().trim();
            int periodPosition = periodSpinner.getSelectedItemPosition();
            double displayAmount = CurrencyManager.parseDisplayAmount(amountStr);
            double amount = CurrencyManager.toBaseCurrency(requireContext(), displayAmount);
            Category selectedCategory = categoryList.get(categoryPosition);
            String period = periods[periodPosition];
            Budget existingBudget = budgetDao.getBudgetByCategoryAndUser(currentUserId, selectedCategory.getId());
            if (existingBudget != null) {
                Toast.makeText(requireContext(), "Budget for this category already exists", Toast.LENGTH_SHORT).show();
                return;
            }
            Budget budget = new Budget(currentUserId, selectedCategory.getId(), amount, period);
            budgetDao.insert(budget);
            refreshBudgetList();
            dialog.dismiss();
            Toast.makeText(requireContext(), "Budget added successfully", Toast.LENGTH_SHORT).show();
        });
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showEditDialog(Budget budget) {
        categoryList.clear();
        categoryList.addAll(categoryDao.getAllByUser(currentUserId));

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_budget, null);

        Spinner categorySpinner = dialogView.findViewById(R.id.categorySpinner);
        TextInputEditText amountInput = dialogView.findViewById(R.id.amountInput);
        TextInputLayout amountLayout = dialogView.findViewById(R.id.amountLayout);
        Spinner periodSpinner = dialogView.findViewById(R.id.periodSpinner);
        Button saveButton = dialogView.findViewById(R.id.saveButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);

        if (amountLayout != null) {
            String symbol = CurrencyManager.getCurrencySymbol(requireContext());
            amountLayout.setHint(getString(R.string.amount_with_currency, symbol));
        }

        List<String> categoryNameList = new ArrayList<>();
        for (Category cat : categoryList) {
            categoryNameList.add(cat.getName());
        }

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, categoryNameList);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);

        Category category = categoryDao.getById(budget.getCategoryId());
        if (category != null) {
            int categoryIndex = -1;
            for (int i = 0; i < categoryList.size(); i++) {
                if (categoryList.get(i).getId() == budget.getCategoryId()) {
                    categoryIndex = i;
                    break;
                }
            }
            if (categoryIndex >= 0) {
                categorySpinner.setSelection(categoryIndex);
            }
        }
        categorySpinner.setEnabled(false);

        amountInput.setText(CurrencyManager.formatEditableValue(requireContext(), budget.getAmount()));

        String[] periods = {"Monthly", "Weekly"};
        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, periods);
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        periodSpinner.setAdapter(periodAdapter);

        int periodIndex = -1;
        for (int i = 0; i < periods.length; i++) {
            if (periods[i].equals(budget.getPeriod())) {
                periodIndex = i;
                break;
            }
        }
        if (periodIndex >= 0) {
            periodSpinner.setSelection(periodIndex);
        }

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        saveButton.setOnClickListener(v -> {
            String amountStr = amountInput.getText().toString().trim();
            int periodPosition = periodSpinner.getSelectedItemPosition();

            if (TextUtils.isEmpty(amountStr)) {
                Toast.makeText(requireContext(), "Please enter amount", Toast.LENGTH_SHORT).show();
                return;
            }

            if (periodPosition < 0 || periodPosition >= periods.length) {
                Toast.makeText(requireContext(), "Please select period", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount;
            try {
                double displayAmount = CurrencyManager.parseDisplayAmount(amountStr);
                amount = CurrencyManager.toBaseCurrency(requireContext(), displayAmount);
                if (amount <= 0) {
                    Toast.makeText(requireContext(), "Amount must be greater than 0", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
                return;
            }

            budget.setAmount(amount);
            budget.setPeriod(periods[periodPosition]);
            budgetDao.update(budget);
            refreshBudgetList();
            dialog.dismiss();
            Toast.makeText(requireContext(), "Budget updated successfully", Toast.LENGTH_SHORT).show();
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}


