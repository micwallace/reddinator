package au.com.wallaceit.reddinator.activity;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.tasks.ComposeMessageTask;


public class ComposeMessageActivity extends Activity implements ComposeMessageTask.Callback {

    private Reddinator global;
    private TextView charsLeft;
    private EditText toField;
    private EditText subjectField;
    private EditText textField;
    private Resources resources;

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);
        global = (Reddinator) getApplicationContext();
        resources = getResources();

        charsLeft= (TextView) findViewById(R.id.subject_chars_left);
        subjectField = (EditText) findViewById(R.id.subject);
        toField = (EditText) findViewById(R.id.to);
        textField = (EditText) findViewById(R.id.text);

        subjectField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                charsLeft.setText(resources.getString(R.string.characters_left, (100 - subjectField.getText().toString().length())));
            }
        });
        // get actionbar and set home button, pad the icon
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ImageView view = (ImageView) findViewById(android.R.id.home);
        if (view != null) {
            view.setPadding(5, 0, 5, 0);
        }

        ThemeManager.Theme theme = global.mThemeManager.getActiveTheme("appthemepref");
        int headerColor = Color.parseColor(theme.getValue("header_color"));
        int headerText = Color.parseColor(theme.getValue("header_text"));

        Button submitButton = (Button) findViewById(R.id.submit_button);
        submitButton.getBackground().setColorFilter(headerColor, PorterDuff.Mode.MULTIPLY);
        submitButton.setTextColor(headerText);
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!global.mRedditData.isLoggedIn()){
                    global.mRedditData.initiateLogin(ComposeMessageActivity.this, false);
                } else {
                    if (validateInput()) {
                        String to = toField.getText().toString();
                        String subject = subjectField.getText().toString();
                        String text = textField.getText().toString();
                        (new ComposeMessageTask(global, ComposeMessageActivity.this, new String[]{to, subject, text})).execute();
                    }
                }
            }
        });
    }

    private boolean validateInput(){
        String toText = subjectField.getText().toString();
        if (toText.equals("")){
            global.showAlertDialog(ComposeMessageActivity.this, resources.getString(R.string.whoa), resources.getString(R.string.no_recipient_error));
            return false;
        }
        String subjectText = subjectField.getText().toString();
        if (subjectText.equals("")){
            global.showAlertDialog(ComposeMessageActivity.this, resources.getString(R.string.whoa), resources.getString(R.string.no_subject_error));
            return false;
        } else if (subjectText.length()>100){
            global.showAlertDialog(ComposeMessageActivity.this, resources.getString(R.string.whoa), resources.getString(R.string.subject_too_long_error));
            return false;
        }
        String content = textField.getText().toString();
        if (content.equals("")){
            global.showAlertDialog(ComposeMessageActivity.this, resources.getString(R.string.whoa), resources.getString(R.string.no_message_text_error));
            return false;
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onMessageSent(boolean result, RedditData.RedditApiException exception, String[] args) {
        if (result){
            this.finish();
            Toast.makeText(this, resources.getString(R.string.message_sent), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
