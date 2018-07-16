/*
 * Copyright (C) 2008 ZXing authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.opzwsteel.mzxing.decode;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

import com.opzwsteel.mzxing.CaptureActivity;

import java.util.concurrent.CountDownLatch;

/**
 * This thread does all the heavy lifting of decoding the images.
 */
final class DecodeThread extends Thread {

    private final CaptureActivity mActivity;
    private final CountDownLatch mHandlerInitLatch;
    private Handler mHandler;
    private boolean isBarcode, isQRcode;
    private Rect mFrameRect;

    DecodeThread(CaptureActivity activity, boolean barcode, boolean qrcode, Rect frameRect) {
        this.mActivity = activity;
        this.mFrameRect = frameRect;
        mHandlerInitLatch = new CountDownLatch(1);
        isBarcode = barcode;
        isQRcode = qrcode;
    }

    Handler getHandler() {
        try {
            mHandlerInitLatch.await();
        } catch (InterruptedException ie) {
            // continue?
        }
        return mHandler;
    }

    @Override
    public void run() {
        Looper.prepare();
        mHandler = new DecodeHandler(mActivity, isBarcode, isQRcode, mFrameRect);
        mHandlerInitLatch.countDown();
        Looper.loop();
    }
}
