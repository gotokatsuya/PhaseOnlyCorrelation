package com.example.PhaseOnlyCorrelation;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.ImageView;
import android.widget.TextView;
import org.jtransforms.fft.DoubleFFT_1D;

public class MyActivity extends Activity {

    private int mImageHeight;
    private int mImageWidth;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        phaseOnlyCorrelation();
    }


    private void phaseOnlyCorrelation(){

        long start = SystemClock.currentThreadTimeMillis();

        /**
         * 画像読み込み
         */
        Bitmap imageA = BitmapFactory.decodeResource(getResources(), R.drawable.img_a);
        Bitmap imageB = BitmapFactory.decodeResource(getResources(), R.drawable.img_b);

        imageA = imageA.copy(Bitmap.Config.ARGB_8888, true);
        imageB = imageB.copy(Bitmap.Config.ARGB_8888, true);

        /**
         * 高さと幅の計算
         */
        mImageHeight = imageA.getHeight();
        mImageWidth = imageA.getWidth();

        /**
         * FFTライブラリインスタンス
         */
        DoubleFFT_1D fft = new DoubleFFT_1D(mImageWidth * mImageHeight);

        /**
         * グレースケール化
         */
        int[] pixelsA = new int[mImageWidth * mImageHeight];
        loadPixelsFromBitmap(pixelsA, imageA);
        grayScale(pixelsA);

        /**
         * フーリエ変換
         */
        double[] normalA = new double[mImageWidth * mImageHeight];
        transDouble(normalA, pixelsA);
        double[] fftA = normalA.clone();
        fft.realForward(fftA);

        /**
         * グレースケール化
         */
        int[] pixelsB = new int[mImageWidth * mImageHeight];
        loadPixelsFromBitmap(pixelsB, imageB);
        grayScale(pixelsB);

        /**
         * フーリエ変換
         */
        double[] normalB = new double[mImageWidth * mImageHeight];
        transDouble(normalB, pixelsB);
        double[] fftB = normalB.clone();
        fft.realForward(fftB);


        /**
         * 正規化相互パワースペクトル
         */
        double[] dstNormal = new double[mImageWidth * mImageHeight];
        double[] dstFft = new double[mImageWidth * mImageHeight];
        spectrum(dstNormal, dstFft, normalA, fftA, normalB, fftB);


        /**
         * 逆フーリエ変換
         */
        fft.realInverse(dstNormal, true);
        fft.realInverse(dstFft, true);


        /**
         * ピーク値、位置ずれ量検出
         */
        findMisregistration(dstNormal, dstFft);


        ImageView imageViewA = (ImageView)findViewById(R.id.imageA);
        ImageView imageViewB = (ImageView)findViewById(R.id.imageB);

        updateBitmapPixels(pixelsA, imageA);
        imageViewA.setImageBitmap(imageA);

        updateBitmapPixels(pixelsB, imageB);
        imageViewB.setImageBitmap(imageB);


        long end = SystemClock.currentThreadTimeMillis();

        TextView timeText = (TextView)findViewById(R.id.time);
        timeText.setText("Time : "+ (end-start)+"ms");
    }

    /**
     * 正規化相互相関
     * */
    private void spectrum(double[] dstNormal, double[] dstFft,double[] normalA, double[] fftA, double[] normalB, double[] fftB){
        double spectrum = 0.0;
        // スペクトルの振幅で正規化
        for(int i=0; i< mImageWidth * mImageHeight; i++) {
            spectrum = Math.sqrt(normalA[i]*normalA[i] + fftA[i]*fftA[i]);
            normalA[i] /= spectrum;
            fftA[i] /= spectrum;

            spectrum = Math.sqrt(normalB[i]*normalB[i] + fftB[i]*fftB[i]);
            normalB[i] /= spectrum;
            fftB[i] /= spectrum;

            // 相互相関
            dstNormal[i] = normalA[i]*normalB[i];
            dstFft[i] = fftA[i]*(-fftB[i]);
        }
    }

    /**
     * ピーク値と位置ずれ量検出
     */
    private Point findMisregistration(double[] normalPoc, double[] fftPoc){
        Point pos = new Point();
        double peak = Double.MIN_VALUE;

        for(int y = 0; y < mImageHeight; y++) {
            for(int x = 0; x < mImageWidth; x++) {
                int idx = x + (y * mImageWidth);
                double spectrum = normalPoc[idx] + Math.abs(fftPoc[idx]);
                if(spectrum > peak){
                    peak = spectrum;
                    pos.x = x;
                    pos.y = y;
                }
            }
        }

        TextView peakText = (TextView)findViewById(R.id.peak);
        peakText.setText("Peak : "+peak);

        TextView posText = (TextView)findViewById(R.id.pos);
        posText.setText("Misregistration : "+"("+pos.x+", "+pos.y+")");
        return pos;
    }


    /**
     * ビットマップからピクセルをロード
     */
    private void loadPixelsFromBitmap(int[] pixels, Bitmap bitmap) {
        bitmap.getPixels(pixels, 0, mImageWidth, 0, 0, mImageWidth, mImageHeight);
    }


    /**
     * Bitmap内の全ピクセルを更新
     */
    private void updateBitmapPixels(int[] pixels, Bitmap bitmap) {
        bitmap.setPixels(pixels, 0, mImageWidth, 0, 0, mImageWidth, mImageHeight);
    }


    /**
     * グレースケール化
     */
    private void grayScale(int[] pix){
        for (int y = 0; y < mImageHeight; y++) {
            for (int x = 0; x < mImageWidth; x++) {
                int idx = x + (y * mImageWidth);
                int red   = pix[idx] & 0x00ff0000 >> 16;
                int green = pix[idx] & 0x0000ff00 >> 8;
                int blue  = pix[idx] & 0x000000ff;

                double dRed   = red   * 0.222015;
                double dGreen = green * 0.706655;
                double dBlue  = blue  * 0.071330;
                double dGray  = dRed + dGreen + dBlue;
                int gray = (int) dGray;

                pix[idx] = Color.rgb(gray, gray, gray);
            }
        }
    }


    /**
     * int配列からdouble配列へ
     */
    private void transDouble(double[] doubles, int[] data){
        int size = mImageWidth * mImageHeight;
        for(int i=0;i<size;i++){
            doubles[i] = data[i];
        }
    }

}
