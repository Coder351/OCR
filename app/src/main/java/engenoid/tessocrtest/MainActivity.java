package engenoid.tessocrtest;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.view.menu.BaseMenuPresenter;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import engenoid.tessocrtest.Core.CameraEngine;
import engenoid.tessocrtest.Core.ExtraViews.FocusBoxView;
import engenoid.tessocrtest.Core.Imaging.Tools;
import engenoid.tessocrtest.Core.TessTool.TessAsyncEngine;

import com.googlecode.leptonica.android.Binarize;
import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.leptonica.android.WriteFile;

public class MainActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener,
        Camera.PictureCallback, Camera.ShutterCallback {

    static final String TAG = "DBG_" + MainActivity.class.getName();

    Button shutterButton;
    Button focusButton;
    FocusBoxView focusBox;
    SurfaceView cameraFrame;
    CameraEngine cameraEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        Log.d(TAG, "Surface Created - starting camera");

        if (cameraEngine != null && !cameraEngine.isOn()) {
            cameraEngine.start();
        }

        if (cameraEngine != null && cameraEngine.isOn()) {
            Log.d(TAG, "Camera engine already on");
            return;
        }

        cameraEngine = CameraEngine.New(holder);
        cameraEngine.start();

        Log.d(TAG, "Camera engine started");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    protected void onResume() {
        super.onResume();

        cameraFrame = (SurfaceView) findViewById(R.id.camera_frame);
        shutterButton = (Button) findViewById(R.id.shutter_button);
        focusBox = (FocusBoxView) findViewById(R.id.focus_box);
        focusButton = (Button) findViewById(R.id.focus_button);

        shutterButton.setOnClickListener(this);
        focusButton.setOnClickListener(this);

        SurfaceHolder surfaceHolder = cameraFrame.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        cameraFrame.setOnClickListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (cameraEngine != null && cameraEngine.isOn()) {
            cameraEngine.stop();
        }

        SurfaceHolder surfaceHolder = cameraFrame.getHolder();
        surfaceHolder.removeCallback(this);
    }

    @Override
    public void onClick(View v) {
        if(v == shutterButton){
            if(cameraEngine != null && cameraEngine.isOn()){
                cameraEngine.takeShot(this, this, this);
            }
        }

        if(v == focusButton){
            if(cameraEngine!=null && cameraEngine.isOn()){
                cameraEngine.requestFocus();
            }
        }
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {

        Log.d(TAG, "Picture taken");

        if (data == null) {
            Log.d(TAG, "Got null data");
            return;
        }

        Bitmap bmp = Tools.getFocusedBitmap(this, camera, data, focusBox.getBox());//
        Log.d(TAG, "Got bitmap");

        //Here do the preprocessing before calling Tess ocr engine
        Log.d(TAG, "<---Starting the preprocessing--->");
        Log.d(TAG, "<---Captured bitmap info--->" + "H: " + bmp.getHeight() + ", W: " + bmp.getWidth());

        //Below tasks too long...
        //Pix myPix = ReadFile.readBitmap(bmp);
        //Log.d(TAG,"" + myPix.getHeight() + myPix.getWidth());
        //Pix result = Binarize.otsuAdaptiveThreshold(myPix, focusBox.getWidth(), focusBox.getHeight(), 100, 100, 0.1F);
        //bmp = WriteFile.writeBitmap(result);
        bmp = binarize(bmp);
        Log.d(TAG," <----this much---->");
        Log.d(TAG, "<----End of Preprocessing--->");

        new TessAsyncEngine().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, this, bmp);

    }

    @Override
    public void onShutter() {

    }

    public Bitmap binarize(Bitmap bmp){
        Log.d(TAG, "CURRENT CONFIG: " + bmp.getConfig());
        //Binarize the bmp of the captured image using a simple threshold method
        int w = bmp.getWidth(); int h = bmp.getHeight(); int size = w*h;
        int[] pixels = new int[size];
        bmp.getPixels(pixels, 0, w, 0,0, w,h);

        //Calculate overall lightness of image
        double avgLightness = 0;
        int lLightness; int pval;
        for (int i=0; i<size; i++){
            pval = pixels[i];
            lLightness = ((pval&0x00FF0000 )>>16) + ((pval& 0x0000FF00 )>>8) + (pval&0x000000FF);
            pixels[i] = lLightness;
            avgLightness += lLightness;
        }
        avgLightness /= size;
        avgLightness *= (5/6.0f);
        Log.d(TAG, "<--Avg lightness: " + avgLightness);

        //Binarize
        Bitmap binaryBmp = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        for (int x=0; x<w; x++){
            for (int y=0; y<h; y++){
                if (pixels[x+w*y] > avgLightness){
                    binaryBmp.setPixel(x,y, Color.WHITE);
                } else{
                    binaryBmp.setPixel(x,y, Color.BLACK);
                }
            }
        }

        Log.d(TAG, "Finished binaryBtm");
        return binaryBmp;
    }

}