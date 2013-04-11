package cogmac.drawjav.video;

import java.io.IOException;

import cogmac.drawjav.Sink;
import cogmac.langx.ref.*;

/**
 * Computes linear difference between two frames, storing difference in the alpha channel.
 * 
 * @author decamp
 */
public class FrameDifferencer implements Sink<IntFrame> {
    
    private final RefPool<IntFrame> mPool = new SoftRefPool<IntFrame>(8);
    private final Sink<? super IntFrame> mSink;
    private final double mGamma;
    private final double mThresh;
    
    private IntFrame mPrev = null;
    private double[] mDiff = null;
    
    
    
    public FrameDifferencer(Sink<? super IntFrame> sink) {
        this(sink, 0.6, 0.0);
    }
    
    
    public FrameDifferencer(Sink<? super IntFrame> sink, double gamma, double thresh) {
        mSink   = sink;
        mGamma  = gamma;
        mThresh = thresh;
    }
    
    
    
    public void consume(IntFrame frame) throws IOException {
        frame.ref();
        
        if(mPrev == null) {
            mPrev = frame;
            return;
        }
        
        mDiff = VideoUtil.computeDifference(mPrev, frame, mDiff);
        
        if(mGamma != 1.0) {
            VideoUtil.pow(mDiff, 0, frame.mWidth * frame.mHeight, mGamma);
        }
        
        if(mThresh > 0) {
            VideoUtil.clampDown(mDiff, 0, frame.mWidth * frame.mHeight, mThresh, 0.0);
        }

        IntFrame out = mPool.poll();
        if(out == null) {
            out = new IntFrame(mPool, frame.mWidth, frame.mHeight);
        }
        
        VideoUtil.setAlpha(frame, mDiff, out);
        
        mPrev.deref();
        mPrev = frame;

        mSink.consume(out);
        out.deref();
    }


    public void clear() {
        doClear();
        mSink.clear();
    }


    public void close() throws IOException {
        doClear();
        mSink.close();
    }

    
    public boolean isOpen() {
        return true;
    }
    
    

    private void doClear() {
        if(mPrev != null) {
            mPrev.deref();
            mPrev = null;
        }
    }
    
}