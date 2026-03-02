package woyou.aidlservice.jiuiv5;

import android.graphics.Bitmap;
import woyou.aidlservice.jiuiv5.ICallback;

interface IWoyouService {
    void sendLCDString(String text, in ICallback callback);
    void sendLCDBitmap(in Bitmap bitmap, in ICallback callback);
}
