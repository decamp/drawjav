/*
* Copyright (c) 2015. Massachusetts Institute of Technology
* Released under the BSD 2-Clause License
* http://opensource.org/licenses/BSD-2-Clause
*/

package bits.drawjav.pipe;

import bits.drawjav.Packet;
import bits.drawjav.StreamHandle;
import bits.drawjav.audio.*;
import bits.jav.util.JavMem;
import bits.jav.util.JavSampleFormat;
import bits.microtime.*;
import bits.util.ref.Refable;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.util.logging.Logger;


/**
* Removes samples from audio packets that fall outside of playback range.
* AudioPacketClipper will also reverse packets during backward playback.
*
* AudioClipper is not thread safe.
*
* @author Philip DeCamp
*/
public class AudioPacketClipper implements SyncClockControl, Filter {

    private static final Logger sLog = Logger.getLogger( AudioPacketClipper.class.getName() );

    public static final long EMPTY_PACKET_MICROS = 100000L;

    private boolean mOpen = true;
    private AudioAllocator mAlloc;
    private AudioFormat    mFormat;

    private final InPad  mSink   = new Input();
    private final OutPad mSource = new Source();

    private final FullClock mClock = new FullClock( Clock.SYSTEM_CLOCK );

    private long    mClipMicros = Long.MIN_VALUE;
    private boolean mForward    = true;

    private AudioPacket  mOutPacket;
    // Indicates outgoing packet has no samples. Interpret as silence or no data.
    private boolean      mOutIsEmpty;
    // Used to cut up large packets when mOutIsEmpty == true (which means mOutPacket has no samples)
    private StreamHandle mOutStream;
    private AudioFormat  mOutFormat;
    private long         mOutStart;
    private long         mOutPos;
    private long         mOutStop;


    public AudioPacketClipper( AudioAllocator optFullAlloc ) {
        if( optFullAlloc == null ) {
            mAlloc = OneStreamAudioAllocator.createPacketLimited( 32, 1204 * 4 );
        } else {
            mAlloc = optFullAlloc;
            mAlloc.ref();
        }
    }


    @Override
    public int inputNum() {
        return 1;
    }

    @Override
    public InPad<AudioPacket> input( int idx ) {
        return mSink;
    }

    @Override
    public int outputNum() {
        return 1;
    }

    @Override
    public OutPad output( int idx ) {
        return mSource;
    }

    @Override
    public void open( EventBus bus ) {
        if( bus != null ) {
            bus.register( this );
        }
    }

    @Override
    public void close() {
        if( !mOpen ) {
            return;
        }
        mOpen = false;
        mAlloc.deref();
        mAlloc = null;
        clear();
    }

    @Override
    public synchronized boolean isOpen() {
        return mOpen;
    }

    @Override
    public void clear() {
        if( mOutPacket != null ) {
            mOutPacket.deref();
            mOutPacket = null;
        }
    }

    @Subscribe
    public void processClockEvent( ClockEvent event ) {
        event.apply( this );
    }

    @Override
    public synchronized void clockStart( long execMicros ) {
        mClock.clockStart( execMicros );
    }

    @Override
    public synchronized void clockStop( long execMicros ) {
        mClock.clockStop( execMicros );
    }

    @Override
    public synchronized void clockSeek( long execMicros, long seekMicros ) {
        mClock.clockSeek( execMicros, seekMicros );
        mClipMicros = seekMicros;
    }

    @Override
    public synchronized void clockRate( long execMicros, Frac rate ) {
        boolean forward = rate.mNum >= 0;
        if( mForward != forward ) {
            mForward = forward;
            mClipMicros = mClock.fromMaster( execMicros );
        }
        mClock.clockRate( execMicros, rate );
    }



    private void initSilenceOutput( AudioPacket ref ) {
        mOutIsEmpty = true;
        mOutStart   = ref.startMicros();
        mOutPos     = mClipMicros;
        mOutStop    = ref.stopMicros();
        mOutStream  = ref.stream();
        mOutFormat  = null;
        if( mOutStream != null ) {
            mOutFormat = mOutStream.audioFormat();
        }
        if( mOutFormat == null ) {
            mOutFormat = ref.toAudioFormat();
        }

        createNextSilencePacket();
    }


    private boolean createNextSilencePacket() {
        long t0;
        long t1;
        if( mForward ) {
            t0 = mOutPos;
            t1 = Math.min( mOutStop, mOutPos + EMPTY_PACKET_MICROS );
            if( t1 <= t0 ) {
                return false;
            }
            mOutPos = t1;
        } else {
            t0 = Math.max( mOutStart, mOutPos - EMPTY_PACKET_MICROS );
            t1 = mOutPos;
            if( t1 <= t0 ) {
                return false;
            }
            mOutPos = t0;
        }

        final int samps = (int)Frac.multLong( t1 - t0, mOutFormat.sampleRate(), 1000000 );
        mOutPacket = mAlloc.alloc( mOutFormat, samps );
        mOutPacket.init( mOutStream, t0, t1, mOutFormat, false );
        mOutPacket.nbSamples( samps );

        final boolean planar = JavSampleFormat.isPlanar( mOutFormat.sampleFormat() );
        final int     chans  = planar ? mOutFormat.channels() : 1;
        final int     len    = mOutPacket.lineSize( 0 );
        for( int i = 0; i < chans; i++ ) {
            JavMem.memset( mOutPacket.extendedDataElem( i ), 0, len );
        }

        return true;
    }



    private class Input extends InPadAdapter<Packet> {

        private final AudioFormat mWork = new AudioFormat();

        @Override
        public int offer( Packet packet ) {
            if( packet == null ) {
                return OKAY;
            }

            if( mOutPacket != null ) {
                return DRAIN_FILTER;
            }

            AudioPacket p = (AudioPacket)packet;
            if( !p.isGap() ) {
                if( mForward ) {
                    mOutPacket = clipForward( p, mClipMicros, mAlloc, mWork );
                } else {
                    mOutPacket = clipBackward( p, mClipMicros, mAlloc, mWork );
                }
                mOutIsEmpty = false;
                return OKAY;
            }

            // Empty packet.
            initSilenceOutput( p );
            return OKAY;
        }

        @Override
        public int status() {
            return mOutPacket == null ? OKAY : DRAIN_FILTER;
        }
    }


    private class Source extends OutPadAdapter {

        @Override
        public int status() {
            return mOutPacket == null ? FILL_FILTER : OKAY;
        }

        @Override
        public int poll( Refable[] out ) {
            if( mOutPacket == null ) {
                return FILL_FILTER;
            }

            out[0] = mOutPacket;
            mOutPacket = null;
            if( mOutIsEmpty ) {
                createNextSilencePacket();
            }
            return OKAY;
        }

    }


    public static AudioPacket clipForward( AudioPacket packet, long clipMicros, AudioAllocator alloc, AudioFormat work ) {
        long t0 = packet.startMicros();
        long t1 = packet.stopMicros();

        if( t0 >= clipMicros ) {
            packet.ref();
            return packet;
        }
        if( t1 <= clipMicros ) {
            return null;
        }

        double p = (double)( clipMicros - t0 ) / ( t1 - t0 );
        final int totalSamples = packet.nbSamples();
        final int writeSamples = Math.max( 0, Math.min( totalSamples, (int)( ( 1.0 - p ) * totalSamples + 0.5 ) ) );

        //final AudioFormat format = packet.audioFormat();
        //final int chans      = packet.channels();
        //final int sampFormat = packet.format();
        //final int sampSize   = JavSampleFormat.getBytesPerSample( sampFormat );

        work.set( packet );
        AudioPacket ret = alloc.alloc( work, totalSamples );
        final int sampSize   = JavSampleFormat.getBytesPerSample( work.mSampleFormat );

        if( writeSamples > 0 ) {
            if( work.mChannels == 1 || !JavSampleFormat.isPlanar( work.mSampleFormat ) ) {
                final int off = (totalSamples - writeSamples) * sampSize * work.mChannels;
                final int len = writeSamples * sampSize * work.mChannels;
                JavMem.copy( packet.dataElem( 0 ) + off, ret.dataElem( 0 ), len );
            } else {
                final int off = (totalSamples - writeSamples) * sampSize;
                final int len = writeSamples * sampSize;
                for( int i = 0; i < work.mChannels; i++ ) {
                    JavMem.copy( packet.extendedDataElem( i ) + off,
                                 ret.extendedDataElem( i ),
                                 len );
                }
            }
        }
        ret.nbSamples( writeSamples );
        ret.init( packet.stream(), clipMicros, t1, work, packet.isGap() );
        return ret;
    }


    public static AudioPacket clipBackward( AudioPacket packet, long clipMicros, AudioAllocator alloc, AudioFormat work ) {
        long t0 = packet.startMicros();
        long t1 = packet.stopMicros();

        if( t0 >= clipMicros ) {
            return null;
        }
        if( t1 <= clipMicros ) {
            packet.ref();
            return packet;
        }

        double p = (double)( clipMicros - t0 ) / ( t1 - t0 );
        final int totalSamples  = packet.nbSamples();
        final int writeSamples  = Math.max( 0, Math.min( totalSamples, (int)(p * totalSamples + 0.5) ) );

        work.set( packet );
        final int sampSize = JavSampleFormat.getBytesPerSample( work.sampleFormat() );
        AudioPacket ret = alloc.alloc( work, totalSamples );

        if( writeSamples != 0 ) {
            if( work.mChannels == 1 || !JavSampleFormat.isPlanar( work.mSampleFormat ) ) {
                long src = packet.dataElem( 0 );
                long dst = packet.dataElem( 0 );
                JavMem.copyReverse( src, dst, writeSamples, sampSize * work.mChannels );
            } else {
                for( int i = 0; i < work.mChannels; i++ ) {
                    long src = packet.extendedDataElem( i );
                    long dst = packet.extendedDataElem( i );
                    JavMem.copyReverse( src, dst, writeSamples, sampSize );
                }
            }
        }

        ret.nbSamples( writeSamples );
        ret.init( packet.stream(), packet.startMicros(), clipMicros, work, packet.isGap() );
        return ret;
    }

}
