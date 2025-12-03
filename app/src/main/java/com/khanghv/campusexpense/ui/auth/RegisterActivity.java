package com.khanghv.campusexpense.ui.auth;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.khanghv.campusexpense.MainActivity;
import com.khanghv.campusexpense.R;
import com.khanghv.campusexpense.base.BaseActivity;
import com.khanghv.campusexpense.data.database.AppDatabase;
import com.khanghv.campusexpense.data.database.UserDao;
import com.khanghv.campusexpense.data.model.User;

import java.security.MessageDigest;

public class RegisterActivity extends BaseActivity {

    private TextView loginText;

    private TextInputLayout usernameLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout comfirmPasswordLayout;

    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private TextInputEditText comfirmPasswordInput;

    private Button btnRegister;
    private UserDao userDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        usernameLayout = findViewById(R.id.usernameLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        comfirmPasswordLayout = findViewById(R.id.comfirmPasswordLayout);

        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        comfirmPasswordInput = findViewById(R.id.comfirmPasswordInput);
        btnRegister = findViewById(R.id.btnRegister);


        loginText = findViewById(R.id.loginText);
        loginText.setOnClickListener(v -> finish());


        AppDatabase database = AppDatabase.getInstance(this);
        userDao = database.userDao();

        btnRegister.setOnClickListener(v -> register());
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return password;
        }
    }

    private void register(){
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String comfirmPassword = comfirmPasswordInput.getText().toString();

        usernameLayout.setError(null);
        passwordLayout.setError(null);
        comfirmPasswordLayout.setError(null);

        if (username.isEmpty()){
            usernameLayout.setError(getString(R.string.error_empty_username));
            return;
        }
        if (password.isEmpty()){
            passwordLayout.setError(getString(R.string.error_empty_password));
            return;
        }

        if (comfirmPassword.isEmpty()) {
            comfirmPasswordLayout.setError(getString(R.string.error_password_mismatch));
            return;
        }


        btnRegister.setEnabled(false);
        btnRegister.setText(R.string.registering);

        new Thread(() -> {
            int count = userDao.checkUsernameExists(username);
            if (count > 0) {
                runOnUiThread(() -> {
                    btnRegister.setEnabled(true);
                    btnRegister.setText(R.string.register);
                    usernameLayout.setError(getString(R.string.error_username_exists));
                });
                return;
            }

            String hashedPassword = hashPassword(password);
            User user = new User(username, hashedPassword);
            long result = userDao.insertUser(user);

            runOnUiThread(() -> {
                btnRegister.setEnabled(true);
                btnRegister.setText(R.string.register);
                if (result > 0) {
                    Toast.makeText(RegisterActivity.this, getString(R.string.register_success), Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(RegisterActivity.this, getString(R.string.register_failed), Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
}
