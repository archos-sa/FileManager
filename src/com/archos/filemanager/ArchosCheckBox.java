/*************************************************************
 ** Replacement widget for the Android checkbox which allow to 
 ** click on the whole object area (with a standard checkbox
 ** the sensitive area is restricted to the checkbox itself)
 *************************************************************/

package com.archos.filemanager;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.CheckBox;


public class ArchosCheckBox extends FrameLayout {
    private final static String TAG = "ArchosCheckBox";

    private CheckBox mCheckBox;
    private FrameLayout mMainContainer;

    public ArchosCheckBox(Context context) {
        super(context, null);
        init(context);
    }

    public ArchosCheckBox(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ArchosCheckBox(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater layoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Make the main container clickable so that it is possible to click anywhere inside the view
        mMainContainer = (FrameLayout)layoutInflater.inflate(R.layout.archos_checkbox, this, true);
        mMainContainer.setClickable(true);
        mMainContainer.setOnClickListener(mOnClickListener);

        // Make the checkbox not clickable to avoid ambiguous click events management
        mCheckBox = (CheckBox)mMainContainer.findViewById(R.id.checkbox);
        mCheckBox.setClickable(false);
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            // It is the main container which intercepts the click events and
            // not the checkbox so we have to toggle the checkbox state manually
            mCheckBox.toggle();
        }
        
    };

    public boolean isChecked() {
        return mCheckBox.isChecked();
    }

    public void setCheckedNoAnimation(boolean checked) {
        mCheckBox.setButtonDrawable(getResources().getDrawable(R.drawable.archos_checkbox_not_animated));
        mCheckBox.setChecked(checked);
    }
    public void setChecked(boolean checked) {
        if( mCheckBox.isChecked()!=checked){
            StateListDrawable   d = (StateListDrawable) getResources().getDrawable(R.drawable.archos_checkbox);
            mCheckBox.setButtonDrawable(d);
            mCheckBox.setChecked(checked);
            ((AnimationDrawable)d.getCurrent()).start();
        }
    }
    public void setChecked(boolean checked, int position) {
        setChecked(checked);
    }
}
