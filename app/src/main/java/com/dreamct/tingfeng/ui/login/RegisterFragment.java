package com.dreamct.tingfeng.ui.login;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.dreamct.tingfeng.utilities.InputValidator;
import com.dreamct.tingfeng.R;
import com.dreamct.tingfeng.data.AppDatabase;
import com.dreamct.tingfeng.data.User;

import java.util.Objects;

public class RegisterFragment extends Fragment {

    private TextInputEditText reAccountInput;
    private TextInputEditText rePasswordInput;
    private TextInputEditText reConfirmInput;
    private Button signUpButton;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化输入控件，为了方便，在类下面已经声明了类型，这里直接赋值
        reAccountInput = view.findViewById(R.id.re_account);
        rePasswordInput = view.findViewById(R.id.re_password);
        reConfirmInput = view.findViewById(R.id.re_confirm_password);
        signUpButton = view.findViewById(R.id.btn_Register);
        //signUpButton.setEnabled(true);

        //初始化注册按钮
        Button signUpButton = view.findViewById(R.id.btn_Register);


        InputValidator.setupTextWatcher(
                new TextInputEditText[]{reAccountInput, rePasswordInput,reConfirmInput},
                signUpButton
        );


        //获取几个输入框的TextInputLayout
        //TextInputLayout accountLayout = view.findViewById(R.id.RQ_account); // 获取TextInputLayout，这里不需要这个框显示错误信息，所以不声明
        TextInputLayout passwordLayout = view.findViewById(R.id.RQ_password); // 获取TextInputLayout
        TextInputLayout confirmLayout = view.findViewById(R.id.RQ_confirm); // 获取TextInputLayout
        signUpButton.setOnClickListener(v -> {

            //让输入框失去焦点
            reAccountInput.clearFocus();
            rePasswordInput.clearFocus();
            reConfirmInput.clearFocus();

            if(rePasswordInput.getText().toString().equals(reConfirmInput.getText().toString())){//有InputValidator，这里不会产生 'NullPointerException'
                passwordLayout.setErrorEnabled(false);
                confirmLayout.setErrorEnabled(false);


                    saveInLocal();



            }else{
                //错误提示，由于用的material3，所以这里用passwordLayout.setError()方法，而不是.setErrorEnabled()方法或passwordInput.setError("不一致");
                passwordLayout.setError(getText(R.string.tips_confirm));
                confirmLayout.setError(getString(R.string.tips_confirm));
            }
        });
        // 可以通过返回按钮或系统返回键返回登录页面
        // 系统返回键已自动处理，如需自定义返回按钮：
        /*
        view.findViewById(R.id.backButton).setOnClickListener(v -> {
            Navigation.findNavController(v).navigateUp();
        });
        */

    }




    private void saveInLocal() {
        final String account = Objects.requireNonNull(reAccountInput.getText()).toString().trim();
        final String rawPassword = Objects.requireNonNull(rePasswordInput.getText()).toString().trim();

        // 后台线程处理数据库操作
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            User existingUser = db.userDao().getUserByAccount(account);

            requireActivity().runOnUiThread(() -> {
                if (existingUser != null) {
                    Toast.makeText(getContext(), "账号已存在", Toast.LENGTH_SHORT).show();
                } /*else if (rawPassword.length() < 6) {
                    Toast.makeText(getContext(), "密码至少6位", Toast.LENGTH_SHORT).show();
                }*/ else {
                    // 生成安全哈希密码，对储存的账户、密码进行加密
                    /*加密，暂时使用明文
                    final String hashedPassword = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
                    final User newUser = new User(account, hashedPassword);
                    */
                    final User newUser = new User(account, rawPassword);

                    // 插入数据库
                    new Thread(() -> {
                        db.userDao().insert(newUser);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "注册成功", Toast.LENGTH_SHORT).show();

                            String temp = reAccountInput.getText().toString();
                            // 创建Bundle传递参数
                            Bundle bundle = new Bundle();
                            bundle.putString("account", temp);
                            /*清空输入框，返回后自动清除，注释掉
                            reAccountInput.setText("");
                            rePasswordInput.setText("");
                            reConfirmInput.setText("");
                             */
                            // 导航到登录页并传递参数。这个方法不是返回式的，返回到原界面需返回两次
                            /*Navigation.findNavController(requireView())
                                    .navigate(R.id.loginFragment, bundle);
                             */
                            getParentFragmentManager()
                                    .setFragmentResult("register_data", bundle);
                            Navigation.findNavController(requireView()).navigateUp();
                        });
                    }).start();
                }
            });
        }).start();
    }
}