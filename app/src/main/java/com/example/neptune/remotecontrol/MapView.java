package com.example.neptune.remotecontrol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Magpie on 4/17/2016.
 */
public class MapView extends View implements RotationGesture.OnRotationListener,ScaleGestureDetector.OnScaleGestureListener, GestureDetector.OnGestureListener{
    ScaleGestureDetector mScaleDet;
    GestureDetector mGestDet;
    RotationGesture mRotGest;
    float mLastRot;


    static final float FTtM=0.3048f;
    float scaleWtoS=1;
    PointF mSize=new PointF(); //device width
    PointF mExtents=new PointF(); //real world meters
    PointF mCenter=new PointF(); // real world meters
    float mAspect;
    float mRot;
    Matrix mWorldToScreen=new Matrix();
    float mScale=1;

    Matrix mScreenToWorld=new Matrix();
    Matrix mScreenScaleToWorld=new Matrix();
    Matrix mScreenScaleTranslate=new Matrix();
    static MainActivity mMainAct;
    Paint xAxisPaint=new Paint();
    Paint zAxisPaint=new Paint();
    Paint mRobotPaint=new Paint();
    Paint mWheelPaint=new Paint();
    Paint mBlackPaint=new Paint();
    Paint obstPaint=new Paint();
    Paint mTargetPaint=new Paint();
    Matrix mRobotModel=new Matrix();
    float mRoboX,mRoboY,mRoboRot;
    float[] mRoboPts={1, 1, -1, 1, -1, 1, 0, -1, 0, -1, 1, 1, -1.1f, .4f, 1.1f, .4f,
            -1.1f, .9f, -1.1f, -.1f, 1.1f, .9f, 1.1f, -.1f};
    float[] mRoboPtsBuf=new float[mRoboPts.length];

    ArrayList<PointF> mTargets=new ArrayList<>();
    ArrayList<String> mTargetNames=new ArrayList<>();

    static final int NDEPTHPTS=9 ;
    PointF[] mDepthPts=new PointF[NDEPTHPTS];
    float[] mDepthPtsBuf=new float[NDEPTHPTS*2];
    float[] mDepthValue=new float[NDEPTHPTS];
    int mDepthIndex=0;

    static final int NOBSTPTS=2000;
    float[] obstPt = new float[NOBSTPTS];
    float[] obstBuf = new float[NOBSTPTS];
    int nObstPt,nObstPtUsed;

    boolean mFirstPerson;


    private static final int NUM_PAINTS=10;
    Paint paint[]=new Paint[NUM_PAINTS];


    public void clearObstacles(){
        nObstPt=0;
        nObstPtUsed=0;
        postInvalidate();
    }

    public void addObstPt(float x,float z){
        float[] t={x,z};
        //mRobotModelInverse.mapPoints(t);
//        mWorldInverse.mapPoints(t);
        //mRobotModelInverse.mapPoints(t);

        //search if point is already in list
        boolean found=false;
        float thresh=.07f;
        for(int i=0;i<obstPt.length/2;i++){
            float tx=obstPt[i*2];
            float ty=obstPt[i*2+1];
            if(Math.abs(tx-t[0])<thresh && Math.abs(ty-t[1])<thresh){
                found=true;
                break;
            }
        }

        if(!found) {
            obstPt[nObstPt * 2] = t[0];
            obstPt[nObstPt * 2 + 1] = t[1];
            nObstPt++;
            if (nObstPt == obstPt.length / 2)
                nObstPt = 0;

            nObstPtUsed++;
            nObstPtUsed=Math.min(nObstPtUsed,obstPt.length/2);
        }
    }
    private void drawObstPts(Canvas canvas){
        mWorldToScreen.mapPoints(obstBuf,obstPt);
        for(int i=0;i<nObstPtUsed;i++){
            canvas.drawCircle(obstBuf[i*2],obstBuf[i*2+1],4,obstPaint);
        }
        //canvas.drawPoints(obstBuf,obstPaint);
    }

    public void addTarget(float x, float y, String name){
        mTargets.add(new PointF(x, y));
        mTargetNames.add(name);
        postInvalidate();
    }
    public void drawTargets(Canvas canvas){
        int n=1;
        for(PointF pt :mTargets){
            PointF dp = toDevice(pt);
            canvas.drawCircle(dp.x,dp.y,5,mTargetPaint);
            canvas.drawText(mTargetNames.get(n-1),dp.x+5,dp.y+5,zAxisPaint);
            n++;
        }
    }

    public void addDepthPt(float u,float v,float z){//float[3]
        //float perspFact=.5f;
        //float gridOffset=.8f;
        //v=1-v;
        mDepthPts[mDepthIndex] = new PointF(u-.5f, z);
        mDepthValue[mDepthIndex] = z;
        mDepthIndex++;if(mDepthIndex==NDEPTHPTS)mDepthIndex=0;
        postInvalidate();

        //Log.e("NUM",pt.x+" "+pt.y);

        //schedule timer to remove point in 1 second
        //new Timer().schedule(new RemoveDepthPtTask(index),1000);
    }
    class RemoveDepthPtTask extends TimerTask{
        int index;
        RemoveDepthPtTask(int i){index=i;}
        public void run(){
            removeDepthPt(index);
        }
    }

    private void removeDepthPt(int index){
       // mDepthPts.remove(index);
    }

    private void drawDepthPts(Canvas canvas){
        float diam = 6*mScale;
        paint[4].setColor(Color.rgb(0,150,100));

        for(int i=0;i<NDEPTHPTS;i++){
            float[] fpt = {mDepthPts[i].x, mDepthPts[i].y};
            mRobotModel.mapPoints(fpt);
            mWorldToScreen.mapPoints(fpt);
            float d=mDepthValue[i]*5;
            if(d==0){
                paint[4].setColor(Color.BLACK);
            }
            canvas.drawCircle(fpt[0], fpt[1], diam, paint[4]);
        }
   }

    public void clearTargets(){
        mTargets.clear();
        mTargetNames.clear();
        postInvalidate();
    }

    public void setRobot(float x, float y, float rot){
        rot*=180/Math.PI;
        rot=rot - 90;

        float dr=rot-mRoboRot;
        mRoboRot=rot;

        float dx=x-mRoboX;
        float dy=y-mRoboY;
        mRoboX=x;
        mRoboY=y;

        if(!mFirstPerson){
            mRobotModel.preRotate(dr);
            mRobotModel.postTranslate(dx, dy);
        }
        else{

        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas){
        if(isInEditMode())
            return;
        drawGrid(canvas);
        drawAxex(canvas);
        drawRobot(canvas);
        canvas.drawLine(1, 1, 1, mSize.y - 1, mBlackPaint);

        drawTargets(canvas);

        drawDepthPts(canvas);
        drawObstPts(canvas);
    }

    void drawAxex(Canvas canvas){
        float[] xpts={0, 0, 1, 0, 1, 0, .8f, .2f, 1, 0, .8f, -.2f};
        float[] zpts={0, 0, 0, 1, 0, 1, .2f, .8f, 0, 1, -.2f, .8f};
        mWorldToScreen.mapPoints(xpts);
        mWorldToScreen.mapPoints(zpts);
        canvas.drawLines(xpts, xAxisPaint);
        canvas.drawLines(zpts, zAxisPaint);
    }

    void drawRobot(Canvas canvas){
        float[] dot={0, 0};
        mRobotModel.mapPoints(mRoboPtsBuf, mRoboPts);
        mRobotModel.mapPoints(dot);
        mWorldToScreen.mapPoints(mRoboPtsBuf);
        mWorldToScreen.mapPoints(dot);
        canvas.drawLines(mRoboPtsBuf, 0, 16, mRobotPaint);
        canvas.drawLines(mRoboPtsBuf, 16, 8, mWheelPaint);
        canvas.drawCircle(dot[0], dot[1], 5, zAxisPaint);
    }

    void drawGrid(Canvas canvas){
        ArrayList<Float> list=new ArrayList<>();
        float xf=mAspect*3.5f, yf=3.5f;
        for(float x=0; x<=mExtents.x*xf + mCenter.x; x+=FTtM){
            list.add(x);
            list.add(mExtents.y*yf + mCenter.y);
            list.add(x);
            list.add(-mExtents.y*yf + mCenter.y);
        }
        for(float x=0; x>=-mExtents.x*xf + mCenter.x; x-=FTtM){
            list.add(x);
            list.add(mExtents.y*yf + mCenter.y);
            list.add(x);
            list.add(-mExtents.y*yf + mCenter.y);
        }
        for(float y=0; y<=mExtents.y*yf + mCenter.y; y+=FTtM){
            list.add(mExtents.x*xf + mCenter.x);
            list.add(y);
            list.add(-mExtents.x*xf + mCenter.x);
            list.add(y);
        }
        for(float y=0; y>=-mExtents.y*yf + mCenter.y; y-=FTtM){
            list.add(mExtents.x*xf + mCenter.x);
            list.add(y);
            list.add(-mExtents.x*xf + mCenter.x);
            list.add(y);
        }
        float[] pts=new float[list.size()];
        for(int i=0; i<list.size(); i++)
            pts[i]=list.get(i);
        mWorldToScreen.mapPoints(pts);
        canvas.drawLines(pts, paint[2]);
    }

    PointF toDevice(PointF p){
        float x=(p.x - mCenter.x)*mSize.x/mExtents.x;
        float y=(p.y - mCenter.y)*mSize.y/mExtents.y;
        float pt[]={p.x, p.y};
        float dest[]=new float[2];
        mWorldToScreen.mapPoints(dest, pt);
        return new PointF(dest[0], dest[1]);
    }

//    public MapView(Context context){
//        super(context);
//        init(context);
//    }

    public MapView(Context context, AttributeSet attrs){
        super(context, attrs);
        if(isInEditMode())
            return;
        mMainAct=(MainActivity)context;
        mScaleDet=new ScaleGestureDetector(context, this);
        mGestDet=new GestureDetector(context, this);
        init(context);
    }

    private void init(Context context){
        mScaleDet=new ScaleGestureDetector(context, this);
        mGestDet=new GestureDetector(context, this);
        mRotGest=new RotationGesture(this);
        //this.setOnTouchListener(this);
        for(int i=0; i<NUM_PAINTS; i++)
            paint[i]=new Paint();
        for (int i = 0; i < NDEPTHPTS; i++)
            mDepthPts[i] = new PointF();
        for(int i=0;i<mRoboPts.length;i++)
            mRoboPts[i]*=.1f;
        paint[0].setStyle(Paint.Style.FILL);
        paint[0].setColor(Color.RED);
        paint[1].setColor(Color.rgb(0, 180, 0));
        paint[1].setStrokeWidth(3);
        paint[1].setStyle(Paint.Style.STROKE);
        paint[2].setColor(Color.rgb(180, 180, 180));
        paint[2].setStrokeWidth(0);
        paint[3].setStyle(Paint.Style.STROKE);
        paint[3].setStrokeWidth(2);
        paint[3].setColor(Color.BLUE);
        paint[4].setStyle(Paint.Style.STROKE);
        paint[4].setStrokeWidth(2);
        paint[4].setColor(Color.rgb(200,100,40));
        obstPaint.setStyle(Paint.Style.STROKE);
        obstPaint.setColor(Color.rgb(180,0,0));
        obstPaint.setStrokeWidth(3);
        xAxisPaint.setColor(Color.RED);
        xAxisPaint.setStrokeWidth(2);
        zAxisPaint.setColor(Color.BLUE);
        zAxisPaint.setStrokeWidth(2);
        mRobotPaint.setColor(Color.rgb(200, 150, 10));
        mRobotPaint.setStrokeWidth(4);
        mWheelPaint.setColor(Color.BLACK);
        mWheelPaint.setStrokeWidth(4);
        mBlackPaint.setColor(Color.BLACK);
        mBlackPaint.setStrokeWidth(2);
        mTargetPaint.setColor(Color.MAGENTA);
        mTargetPaint.setStyle(Paint.Style.FILL);
        setRobot(1, 1, -30);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh){
        super.onSizeChanged(w, h, oldw, oldh);
        mSize=new PointF(w, h);
        //mMainAct.dump(w + " x " + h);
        mAspect=mSize.x/mSize.y;
        //mMainAct.dump("aspect: " + mAspect);
        setExtCenterRot(2.5f, 2.5f, 0, 0, 5);


    }

    void setTransform(){
        RectF screen=new RectF(0, 0, mSize.x, mSize.y);
        RectF world=new RectF(-mExtents.x, -mExtents.y, mExtents.x, mExtents.y);
        mWorldToScreen.setRectToRect(world, screen, Matrix.ScaleToFit.CENTER);
        mWorldToScreen.preScale(1, -1);
        mWorldToScreen.invert(mScreenScaleToWorld);
        mWorldToScreen.preTranslate(-mCenter.x, -mCenter.y);
        mWorldToScreen.invert(mScreenScaleTranslate);
        mWorldToScreen.preRotate(mRot);
        mWorldToScreen.invert(mScreenToWorld);
    }

    void transform(float dx, float dy, float scl,float rot){
        mScale*=scl;

        mWorldToScreen.preRotate(rot);
        mWorldToScreen.postTranslate(dx,-dy);
        mWorldToScreen.preScale(scl,scl);
         postInvalidate();
    }

    public void setExtCenterRot(float x, float y, float cx, float cy, float rot){
        if(y<=x)
            mExtents=new PointF(x, x);
        else
            mExtents=new PointF(y, y);
        mCenter.x=cx;
        mCenter.y=cy;
        mRot=rot;
        setTransform();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        //mMainAct.dump("Fingers " + event.getPointerCount());
        mRotGest.onTouchEvent(event);
        mGestDet.onTouchEvent(event);
        mScaleDet.onTouchEvent(event);
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY){
        transform(-distanceX,distanceY,1,0);
        return true;
    }

    @Override
    public void OnRotation(RotationGesture rotationDetector){
        transform(0,0,1,rotationDetector.getAngle());
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector){

        float f=mScaleDet.getScaleFactor();
        //mMainAct.dump("scalefactor "+f);

        transform(0,0,f,0);

        return true;
    }


    ///////////////unused methods that require implementation
    @Override
    public boolean onDown(MotionEvent e){
        return true;
    }
    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
        return false;
    }
    @Override
    public void onShowPress(MotionEvent e){

    }
    @Override
    public boolean onSingleTapUp(MotionEvent e){
        mMainAct.dump("tap");
        return true;
    }
    @Override
    public void onLongPress(MotionEvent e){
        mMainAct.dump("long");
    }
    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector){
        return true;
    }
    @Override
    public void onScaleEnd(ScaleGestureDetector detector){
    }
}
