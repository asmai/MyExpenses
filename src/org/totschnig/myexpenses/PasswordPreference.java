package org.totschnig.myexpenses;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.preference.DialogPreference;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;

public class PasswordPreference extends DialogPreference implements TextWatcher, OnCheckedChangeListener {
  
  private boolean boolProtect;
  private String strPass1;
  private String strPass2;
  private EditText password1;
  private EditText password2;
  private CheckBox protect;
  private TextView error;
  
  public PasswordPreference(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setDialogLayoutResource(R.layout.password_dialog);
    }
     
    public PasswordPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    setDialogLayoutResource(R.layout.password_dialog);
    }
    @Override
    protected void onDialogClosed(boolean positiveResult) {
      MyApplication app = MyApplication.getInstance();
      super.onDialogClosed(positiveResult);

      if (positiveResult) {
        Editor editor = getEditor();
        editor.putBoolean(MyApplication.PREFKEY_PERFORM_PROTECTION, boolProtect);
        if (boolProtect && strPass1 != null && strPass1.equals(strPass2)) {
          String hash = Utils.md5(strPass1);
          editor.putString(MyApplication.PREFKEY_SET_PASSWORD, hash);
          app.passwordHash = hash;
        }
        editor.commit();
      }
    }
    @Override
    protected void onBindDialogView(View view) {
      password1    = (EditText) view.findViewById(R.id.password1);
      password2    = (EditText) view.findViewById(R.id.password2);
      protect = (CheckBox) view.findViewById(R.id.performProtection);
      error        = (TextView) view.findViewById(R.id.passwordNoMatch);

      password1.addTextChangedListener(this);
      password2.addTextChangedListener(this);
      protect.setOnCheckedChangeListener(this);
      super.onBindDialogView(view);
   }

    @Override
    public void afterTextChanged(Editable s) {
      validate();
    }
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
      validate();
    }
    private void validate() {
      Dialog dlg = getDialog();
      Button btn = ((AlertDialog)dlg).getButton(AlertDialog.BUTTON_POSITIVE);

      boolProtect = protect.isChecked();
      strPass1 = password1.getText().toString();
      strPass2 = password2.getText().toString();

      if (!boolProtect) {
        btn.setEnabled(true);
        return;
      }
      if (strPass1.equals("")) {
        error.setText(R.string.pref_password_empty);
        btn.setEnabled(false);
        return;
      }
      if (strPass1.equals(strPass2)) {
          error.setText("");
          btn.setEnabled(true);
      } else {
        error.setText(R.string.pref_password_not_equal);
        btn.setEnabled(false);
      }
    }
}