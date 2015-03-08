/*
* Copyright (c) 2015. Massachusetts Institute of Technology
* Released under the BSD 2-Clause License
* http://opensource.org/licenses/BSD-2-Clause
*/

package bits.drawjav.pipe;

import bits.drawjav.*;
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

    private final MemoryManager mOptMem;

    private final InPad     mInput  = new InHandler();
    private final OutPad    mOutput = new OutHandler();
    private final FullClock mClock  = new FullClock( Clock.SYSTEM_CLOCK );

    private boolean mOpen = false;
    private AudioAllocator mAlloc;

    private long    mClipMicros = Long.MIN_VALUE;
    private boolean mForward    = true;

    private DrawPacket   mOutPacket;
    private boolean      mOutIsGap;

    // Used to cut up large packets when mOutIsGap == true (which means mOutPacket has no samples)
    private StreamHandle mOutStream;
    private AudioFormat  mOutFormat;
    private long         mOutStart;
    private long         mOutPos;
    private long         mOutStop;


    public AudioPacketClipper( MemoryManager optMem ) {
        mOptMem = optMem;
    }


    @Override
    public int inputNum() {
        return 1;
    }

    @Override
    public InPad<DrawPacket> input( int idx ) {
        return mInput;
    }

    @Override
    public int outputNum() {
        return 1;
    }

    @Override
    public OutPad output( int idx ) {
        return mOutput;
    }

    @Override
    public void open( EventBus bus ) {
        if( mOpen ) {
            return;
        }

        mOpen = true;
        if( mOptMem == null ) {
            mAlloc = OneFormatAudioAllocator.createPacketLimited( 32, 1024 * 4 );
        } else {
            mAlloc = mOptMem.audioAllocator( mOutStream );
        }

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


    private class InHandler extends InPadAdapter<DrawPacket> {

        private final AudioFormat mWork = new AudioFormat();

        @Override
        public int offer( DrawPacket packet ) {
            if( packet == null ) {
                return OKAY;
            }

            packet.stream( mOutStream );
            if( mOutPacket != null ) {
                return DRAIN_FILTER;
            }
            mOutIsGap = false;

            if( !packet.isGap() ) {
                mOutIsGap = false;
                if( mForward ) {
                    mOutPacket = clipForward( packet, mClipMicros, mAlloc, mWork );
                } else {
                    mOutPacket = clipBackward( packet, mClipMicros, mAlloc, mWork );
                }
                return OKAY;
            }

            // Empty packet.
            long start = packet.startMicros();
            long stop = packet.stopMicros();
            if( mForward ) {
                if( stop <= mClipMicros ) {
                    // Entire packet is clipped. Just drop it.
                    return OKAY;
                }

                // Setup gap information.
                mOutIsGap = true;
                mOutPos = Math.max( start, mClipMicros );
                mOutStart = start;
                mOutStop = stop;

            } else {
                if( mClipMicros <= start ) {
                    // Entire packet is clipped. Just drop it.
                    return OKAY;
                }

                // Setup gap information.
                mOutIsGap = true;
                mOutPos = Math.min( stop, mClipMicros );
                mOutStart = start;
                mOutStop = stop;
            }

            createNextSilencePacket();
            return OKAY;
        }

        @Override
        public int status() {
            return mOutPacket == null ? OKAY : DRAIN_FILTER;
        }
    }


    private class OutHandler extends OutPadAdapter {
        @Override
        public void config( StreamHandle stream ) {
            mOutStream = stream;
            mOutFormat = stream.audioFormat();
        }

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

//            System.out.print( "AudioPacketClippper: " ); Debug.print( (DrawPacket)out[0] );
            if( mOutIsGap ) {
                createNextSilencePacket();
            }

            return OKAY;
        }
    }


    public static DrawPacket clipForward( DrawPacket packet, long clipMicros, AudioAllocator alloc, AudioFormat work ) {
        long t0 = packet.startMicros();
        long t1 = packet.stopMicros();

        if( t0 >= clipMicros ) {
            packet.ref();
            return packet;
        }

        if( t1 <= clipMicros ) {
            return null;
        }

        double p = (double)(clipMicros - t0) / (t1 - t0);
        final int totalSamples = packet.nbSamples();
        final int writeSamples = Math.max( 0, Math.min( totalSamples, (int)((1.0 - p) * totalSamples + 0.5) ) );

        //final AudioFormat format = packet.audioFormat();
        //final int chans      = packet.channels();
        //final int sampFormat = packet.format();
        //final int sampSize   = JavSampleFormat.getBytesPerSample( sampFormat );

        work.set( packet );
        DrawPacket ret = alloc.alloc( work, totalSamples );
        final int sampSize = JavSampleFormat.getBytesPerSample( work.mSampleFormat );

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


    public static DrawPacket clipBackward( DrawPacket packet,
                                           long clipMicros,
                                           AudioAllocator alloc,
                                           AudioFormat work )
    {
        long t0 = packet.startMicros();
        long t1 = packet.stopMicros();

        if( t0 >= clipMicros ) {
            return null;
        }
        if( t1 <= clipMicros ) {
            packet.ref();
            return packet;
        }

        double p = (double)(clipMicros - t0) / (t1 - t0);
        final int totalSamples  = packet.nbSamples();
        final int writeSamples  = Math.max( 0, Math.min( totalSamples, (int)(p * totalSamples + 0.5) ) );

        work.set( packet );
        final int sampSize = JavSampleFormat.getBytesPerSample( work.sampleFormat() );
        DrawPacket ret = alloc.alloc( work, totalSamples );

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
