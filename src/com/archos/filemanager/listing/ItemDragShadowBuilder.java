package com.archos.filemanager.listing;

import com.archos.filemanager.ArchosCheckBox;
import com.archos.filemanager.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


public class ItemDragShadowBuilder extends DragShadowBuilder {

    private static final String TAG = "ItemDragShadowBuilder";

    private final Bitmap mIcon;
    private String mName;

    private final int mDragWidth;
    private final int mDragHeight;
    private final Drawable mBackground;
    private final StaticLayout mTextLayout;
    private final PointF mTextPoint;
    private final PointF mIconPoint;

    private final int mTouchPointX;

    public ItemDragShadowBuilder(Context context, View view, int touchPointX, String name, int iconResId) {
        super(view);
        mName = name;
        mIcon = ((BitmapDrawable)(context.getResources().getDrawable(iconResId))).getBitmap();

        mDragWidth = view.getWidth();
        mDragHeight = view.getHeight();
        mTouchPointX = touchPointX;

        Resources res = view.getResources();
        mBackground = res.getDrawable(R.drawable.list_activated_holo);
        mBackground.setBounds(0, 0, mDragWidth, mDragHeight);

        ArchosCheckBox selectView = (ArchosCheckBox) view.findViewById(R.id.select);

        ImageView iconView = (ImageView) view.findViewById(R.id.icon);
        mIconPoint = new PointF(iconView.getLeft() - selectView.getWidth(), iconView.getTop());

        TextView nameView = (TextView) view.findViewById(R.id.name);
        TextView infoView = (TextView) view.findViewById(R.id.info);
        LinearLayout textLinesView = (LinearLayout) view.findViewById(R.id.text_lines);
        int textWidth = textLinesView.getWidth() + selectView.getWidth() - textLinesView.getPaddingRight();
        int maxLines = nameView.getLineCount() + infoView.getLineCount();

        TextPaint textPaint = nameView.getPaint();
        mName =  getMultilineEllipsizedText(mName, maxLines, textWidth, textPaint);
        mTextLayout = new StaticLayout(mName, textPaint, textWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        mTextPoint = new PointF(textLinesView.getLeft() - selectView.getWidth(), (view.getHeight() - mTextLayout.getHeight()) / 2);
    }

    @Override
    public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
        shadowSize.set(mDragWidth, mDragHeight);
        shadowTouchPoint.set(mTouchPointX, mDragHeight / 2);
    }

    @Override
    public void onDrawShadow(Canvas canvas) {
        mBackground.draw(canvas);
        canvas.drawBitmap(mIcon, mIconPoint.x, mIconPoint.y, null);
        canvas.save();
        canvas.translate(mTextPoint.x, mTextPoint.y);
        mTextLayout.draw(canvas);
        canvas.restore();
    }

    private String getMultilineEllipsizedText(String text, int lines, int width, TextPaint paint) {
        int len = text.length();
        int spos = 0;
        int hasLines = 0;
        StringBuffer result = new StringBuffer();

        while (hasLines < lines - 1) {
            int cnt = paint.breakText(text, spos, len, true, width, null);
            if (cnt >= len - spos) {
                result.append(text.substring(spos));
                spos += cnt;
                break;
            }

            int tmp = text.lastIndexOf(' ', spos + cnt - 1);
            if (tmp >= spos) {
                result.append(text.substring(spos, tmp + 1));
                spos = tmp + 1;
            } else {
                result.append(text.substring(spos, cnt));
                spos += cnt;
            }
            hasLines++;
        }

        if (spos < len) {
            result.append(TextUtils.ellipsize(text.subSequence(spos, len), paint, (float)width, TextUtils.TruncateAt.END));
        }

        return result.toString();
    }

}

