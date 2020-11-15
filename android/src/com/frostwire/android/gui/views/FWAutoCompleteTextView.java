package com.frostwire.android.gui.views;

import android.content.Context;
import android.util.AttributeSet;

import com.frostwire.android.gui.util.UIUtils;

public class FWAutoCompleteTextView extends androidx.appcompat.widget.AppCompatAutoCompleteTextView {
    private boolean showKeyboardOnPaste;
    
    public FWAutoCompleteTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public FWAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FWAutoCompleteTextView(Context context) {
        super(context);
    }

    public boolean onTextContextMenuItem(int id) {
        // Do your thing:
        boolean consumed = super.onTextContextMenuItem(id);
        // React:
        switch (id){
//            case android.R.id.cut:
//                onTextCut();
//                break;
            case android.R.id.paste:
                onTextPaste();
                break;
//            case android.R.id.copy:
//                onTextCopy();
        }
        return consumed;
    }

    public void setShowKeyboardOnPaste(boolean show) {
        showKeyboardOnPaste = show;
    }
    
    public boolean isShowKeyboardOnPaste() {
        return showKeyboardOnPaste;
    }
    
    private void onTextPaste() {
        if (showKeyboardOnPaste) {
            UIUtils.showKeyboard(getContext(), this);
        }
    }
}
