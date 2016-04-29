package com.example.neptune.remotecontrol;

import android.view.MotionEvent;

public class RotationGesture{
    private int ptrID1= -1, ptrID2= -1;
    private double mAngle;
    private double lastAngle;

    private OnRotationListener mListener;

    public float getAngle() {
        return (float)Math.toDegrees(mAngle);
    }

    public RotationGesture(OnRotationListener listener){
        mListener = listener;
    }

    public boolean onTouchEvent(MotionEvent event){
        double x1,y1,x2,y2;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ptrID1 = event.getPointerId(event.getActionIndex());
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                ptrID2 = event.getPointerId(event.getActionIndex());
                x1 = event.getX(event.findPointerIndex(ptrID1));
                y1 = event.getY(event.findPointerIndex(ptrID1));
                x2 = event.getX(event.findPointerIndex(ptrID2));
                y2 = event.getY(event.findPointerIndex(ptrID2));
                lastAngle=Math.atan2(y2-y1,x2-x1);
                break;
            case MotionEvent.ACTION_MOVE:
                if(ptrID1 != -1 && ptrID2 != -1){
                    x1 = event.getX(event.findPointerIndex(ptrID1));
                    y1 = event.getY(event.findPointerIndex(ptrID1));
                    x2 = event.getX(event.findPointerIndex(ptrID2));
                    y2 = event.getY(event.findPointerIndex(ptrID2));
                    double newAngle=Math.atan2(y2-y1,x2-x1);
                    mAngle=lastAngle-newAngle;
                    lastAngle=newAngle;

                    if (mListener != null) {
                        mListener.OnRotation(this);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                ptrID1 = -1;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                ptrID2 = -1;
                break;
            case MotionEvent.ACTION_CANCEL:
                ptrID1 = -1;
                ptrID2 = -1;
                break;
        }
        return true;
    }


    public interface OnRotationListener {
        void OnRotation(RotationGesture rotationDetector);
    }
}