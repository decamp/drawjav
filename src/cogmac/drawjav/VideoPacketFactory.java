package cogmac.drawjav;

import java.util.*;

import bits.langx.ref.RefPool;

/**
 * @author decamp
 */
public class VideoPacketFactory implements RefPool<VideoPacket> {
    
    private final PictureFormat mFormat;
    private final List<VideoPacket> mQueue = new ArrayList<VideoPacket>();
    private int mCapacity;
    
    
    public VideoPacketFactory(PictureFormat format, int poolSize) {
        mFormat   = format;
        mCapacity = poolSize;
    }
    
    

    public synchronized VideoPacket build(StreamHandle stream, long startMicros, long stopMicros) {
        VideoPacket packet = poll();
        
        if(packet == null) {
            if(mFormat != null) {
                packet = VideoPacket.newFormattedInstance(this, mFormat);
            }else{
                packet = VideoPacket.newAutoInstance(this);
            }
        }
        
        packet.init(stream, mFormat, startMicros, stopMicros);
        return packet;
    }
    
    
    public synchronized boolean offer(VideoPacket obj) {
        if(mCapacity >= 0 && mQueue.size() >= mCapacity)
            return false;
        
        PictureFormat form = obj.pictureFormat();
        
        if(mFormat == null || mFormat.equals(form)) {
            mQueue.add(obj);
            return true;
        }
        
        if(form == null)
            return false;
        
        if( form.width() != mFormat.width() ||
            form.height() != mFormat.height() ||
            form.pixelFormat() != mFormat.pixelFormat() )
        {
            return false;
        }
        
        mQueue.add(obj);
        return true;
    }

    
    public synchronized VideoPacket poll() {
        int n = mQueue.size();
        if(n == 0)
            return null;
        
        VideoPacket p = mQueue.remove(n - 1);
        p.ref();
        return p;
    }
    
    
    public void close() {
        List<VideoPacket> clear;
        
        synchronized(this) {
            if(mCapacity == 0)
                return;
        
            mCapacity = 0;
            clear = new ArrayList<VideoPacket>(mQueue);
            mQueue.clear();
        }
        
        for(VideoPacket p: clear) {
            p.ref();
            p.deref();
        }
    }
    
}
