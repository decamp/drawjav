package bits.drawjav.audio;

import bits.jav.codec.JavFrame;
import bits.util.ref.AbstractRefable;
import bits.util.ref.Refable;

import java.util.*;
import java.util.logging.Logger;


/**
 * @author Philip DeCamp
 */
public class OneStreamAudioAllocator extends AbstractRefable implements AudioAllocator {

    private static final Logger sLog = Logger.getLogger( OneStreamAudioAllocator.class.getName() );

    private final Stack<AudioPacket> mPool = new Stack<AudioPacket>();
    private final long mByteCap;
    private       int  mItemCap;
    private       int  mDefaultSampleNum;


    private AudioFormat mPoolFormat;
    private long        mPoolSize;
    private int         mPoolItemSize;

    private boolean mHasFormat        = false;
    private boolean mHasChangedFormat = false;
    private boolean mDropping         = false;

    // Number of frames not returned.
    private int mOutstandingCount       = 0;
    private int mOutstandingCountThresh = 1000;


    public OneStreamAudioAllocator( int itemMax, long byteMax, int defaultSampleNum ) {
        mItemCap = itemMax;
        mByteCap = byteMax;
        mDefaultSampleNum = defaultSampleNum;
    }


    public synchronized AudioPacket alloc( AudioFormat format ) {
        return alloc( format, mDefaultSampleNum );
    }

    @Override
    public synchronized AudioPacket alloc( AudioFormat format, int numSamples ) {
        if( !checkFormat( format, mPoolFormat ) ) {
            format = setPoolFormat( format );
        }

        mHasFormat = true;
        AudioPacket ret = poll();

        mOutstandingCount++;
        if( mOutstandingCount > mOutstandingCountThresh && mOutstandingCountThresh > 0 ) {
            mOutstandingCountThresh = -1;
            sLog.warning( "Audio frames not being recycled. There is likely a memory leak." );
        }

        if( ret != null ) {
            if( format == null || numSamples < 0 || ret.nbSamples() > numSamples ) {
                return ret;
            }
            drop( ret );
        }

        if( format != null && numSamples >= 0 ) {
            return AudioPacket.createFilled( this, format, numSamples, 0 );
        } else {
            return AudioPacket.createAuto( this );
        }
    }


    public synchronized boolean offer( AudioPacket packet ) {
        if( mDropping ) {
            return false;
        }

        mOutstandingCount--;

        if( mItemCap >= 0 && mPool.size() >= mItemCap ) {
            return false;
        }

        AudioFormat fmt = packet.audioFormat();
        if( !checkFormat( fmt, mPoolFormat ) ) {
            return false;
        }

        int itemSize = itemSize( packet );
        if( mByteCap >= 0 && mPoolSize + itemSize >= mByteCap ) {
            return false;
        }

        mPool.push( packet );
        mPoolSize += itemSize;
        return true;
    }


    public synchronized AudioPacket poll() {
        int n = mPool.size();
        switch( n ) {
        case 0:
            return null;
        case 1:
            mPoolSize = 0;
            return mPool.pop();
        default:
            AudioPacket p = mPool.pop();
            mPoolSize -= itemSize( p );
            return p;
        }
    }


    @Override
    protected void freeObject() {
        mItemCap = 0;

        List<Refable> clear;
        synchronized( this ) {
            if( mPool.isEmpty() ) {
                return;
            }
            clear = new ArrayList<Refable>( mPool );
            mPool.clear();
        }

        for( Refable p : clear ) {
            drop( p );
        }
    }



    private boolean checkFormat( AudioFormat a, AudioFormat b ) {
        return a == b || a != null && a.equals( b );
    }


    private void drop( Refable ref ) {
        // Refable object likely to offer itself on deref, thus the "mDropping" var.
        // Would be better to have separate method, but I didn't want to change the Refable API.
        mDropping = true;
        ref.deref();
        mDropping = false;
    }


    private int itemSize( AudioPacket packet ) {
        if( mByteCap < 0 ) {
            return 0;
        }

        int size = 0;
        if( packet.hasDirectBuffer() ) {
            size = packet.directBufferCapacity();
        } else {
            size = JavFrame.computeAudioBufferSize( packet.channels(), packet.nbSamples(), packet.format(), 0, null );
        }
        return Math.max( 0, size ) + 256;
    }


    private AudioFormat setPoolFormat( AudioFormat format ) {
        if( !AudioFormats.isFullyDefined( format ) ) {
            return mPoolFormat;
        }

        mPoolFormat = format;

        if( mHasFormat && !mHasChangedFormat ) {
            mHasChangedFormat = true;
            sLog.warning( "OneStreamAudioAllocator is being used for mutliple video formats. Performance may be degraded." );
        }

        while( !mPool.isEmpty() ) {
            drop( mPool.pop() );
        }

        return format;
    }

}

