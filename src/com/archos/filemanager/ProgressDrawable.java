package com.archos.filemanager;

import com.archos.filemanager.FileManagerService.ServiceListener;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class ProgressDrawable  extends View implements ServiceListener{

    /*
     * created by alexandre r
     */
    private int viewWidth;
    private int viewHeight;
    private Paint mPaintProgress;
    private Paint mPaintBackground;
    private int mProgress =0;
    private int mBackgroundColor;
    private int mProgressColor;
    private Context context;
    private Bitmap mBitmap;
    private Drawable mDrawable;
    public ProgressDrawable(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setDefault(context);
    }

    public ProgressDrawable(Context context, AttributeSet attrs) {
        super(context, attrs); 
        setDefault(context);
    }
    public ProgressDrawable(Context context) {
        super(context);
        setDefault(context);
    }
    private void setDefault(Context context) {
        this.context = context;
        mBackgroundColor = context.getResources().getColor(R.color.background_progress);
        mProgressColor = context.getResources().getColor(R.color.background_2014_color);
        
        Resources res = context.getResources();
        mBitmap = BitmapFactory.decodeResource(res, R.drawable.ic_menu_file_copy);
        
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
     
        viewWidth = this.getLayoutParams().width;
        viewHeight = this.getLayoutParams().height;
   }
    /**
     * 
     * 
     * @param progress has to be between 0 and 360
     */
    public void setProgress(int progress){
        mProgress = progress;
        invalidate();
    }

    public void setBackgroundColor(int backgroundColor){
        mBackgroundColor = backgroundColor;
        invalidate();
    }

    public void setDrawable(int resource){
        Resources res = context.getResources();
        mBitmap = BitmapFactory.decodeResource(res, resource);
        invalidate();
    }

    public void setProgressColor(int progressColor){
        mProgressColor = progressColor;
        invalidate();
    }

    public void notifyServiceCreated(){
        FileManagerService.fileManagerService.addListener(this);
    }

    @Override
    public void onDetachedFromWindow(){
        super.onDetachedFromWindow();
        FileManagerService.fileManagerService.deleteObserver(this);
    }

    @Override
    public void onAttachedToWindow(){
        super.onAttachedToWindow();
        if(FileManagerService.fileManagerService!=null)
            FileManagerService.fileManagerService.addListener(this);
    }

    @Override
    public void draw(Canvas canvas) {
        final float startAngle = 0f;
        final float drawTo = mProgress;
        RectF rect = new RectF(0,0,viewWidth,viewHeight);
        if(mPaintProgress==null){
            mPaintProgress = new Paint();
            mPaintProgress.setAntiAlias(true);
            mPaintProgress.setColor(mProgressColor);   
        }
        if(mPaintBackground==null){
            mPaintBackground = new Paint();
            mPaintBackground.setAntiAlias(true);
            mPaintBackground.setColor(mBackgroundColor);
        }
        canvas.rotate(-90f, rect.centerX(), rect.centerY());
        canvas.drawArc(rect, 0, 360, false, mPaintBackground);
        canvas.drawArc(rect, startAngle, drawTo, true, mPaintProgress);
        Paint paint =new Paint();
       if(mBitmap!=null){
           int y = viewHeight/2 -  mBitmap.getHeight()/2;
           int x = viewWidth/2 -  mBitmap.getWidth()/2;
           canvas.drawBitmap(mBitmap,x>0?x:0 , y>0?y:0, paint);
       }
    }

    @Override
    public void onActionStart() {
    }

    @Override
    public void onActionStop() {
    }

    @Override
    public void onActionError() {
    }

    @Override
    public void onActionCanceled() {
    }

    @Override
    public void onProgressUpdate() {
        if(FileManagerService.fileManagerService.getPasteTotalProgress()!=-1)
            setProgress((int) ((float) FileManagerService.fileManagerService.getPasteTotalProgress() / (float) FileManagerService.fileManagerService.getPasteTotalSize() * 360));
        else
            setProgress((int)((float)FileManagerService.fileManagerService.getCurrentFile()/(float)FileManagerService.fileManagerService.getPasteTotalFiles()*360));
    }
}
