package com.olioo.vtw.gui;

import android.content.Context;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.olioo.vtw.MainActivity;

/**
 * Created by olioo on 6/12/2016.
 */
public class jEditText extends android.support.v7.widget.AppCompatEditText {
    private final Context context;

    public String lastValue = "0";
    public AfterEditorAction afterEditorAction;
    private double min = Float.NEGATIVE_INFINITY, max = Float.POSITIVE_INFINITY;

    public jEditText(Context context) {
        super(context);
        this.context = context;
        init();
    }
    public jEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }
    public jEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.context = context;
        init();
    }

    private void init() {
        //setBackground(new ColorDrawable(0xff00ff));
        setSingleLine(true);
        setImeOptions(EditorInfo.IME_ACTION_DONE);
        setPadding(5,0,5,0);

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                lastValue = getText()+"";
            }
        });

        final jEditText jed = this;
        setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((actionId & EditorInfo.IME_ACTION_DONE) != 0) {
                    Message msg = Message.obtain();
                    msg.what = MainActivity.HNDL_HIDE_KEYBOARD;
                    msg.obj = jed;
                    MainActivity.handle.sendMessage(msg);
                }

                String txt = getText()+"";
                if (txt.length() == 0) txt = lastValue;
                else if (txt.equals(".")) txt = lastValue;
                else if (txt.equals("-")) txt = lastValue;
                else lastValue = txt;
                setText(txt);

                if (afterEditorAction != null) afterEditorAction.run(v, actionId, event);
                return true;
            }
        });
    }

    /**
     * <p>This is where the "magic" happens.</p>
     * <p>The menu used to cut/copy/paste is a normal ContextMenu, which allows us to
     *  overwrite the consuming method and react on the different events.</p>
     * @see <a href="http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3_r1/android/widget/TextView.java#TextView.onTextContextMenuItem%28int%29">Original Implementation</a>
     */
    @Override
    public boolean onTextContextMenuItem(int id) {
        // Do your thing:
        boolean consumed = super.onTextContextMenuItem(id);
        // React:
        switch (id){
            case android.R.id.cut:
                onTextCut();
                break;
            case android.R.id.paste:
                onTextPaste();
                break;
            case android.R.id.copy:
                onTextCopy();
        }
        return consumed;
    }

    /**
     * Text was cut from this EditText.
     */
    public void onTextCut(){
        Toast.makeText(context, "Cut!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Text was copied from this EditText.
     */
    public void onTextCopy(){
        Toast.makeText(context, "Copy!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Text was pasted into the EditText.
     */
    public void onTextPaste(){
        Toast.makeText(context, "Paste!", Toast.LENGTH_SHORT).show();
    }

    //FI to append to onEditorActionListener
    public static interface AfterEditorAction {
        void run(TextView v, int actionId, KeyEvent event);
    }
}
