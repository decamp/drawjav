/*
* Copyright (c) 2015. Massachusetts Institute of Technology
* Released under the BSD 2-Clause License
* http://opensource.org/licenses/BSD-2-Clause
*/

package bits.drawjav.pipe;

import bits.drawjav.*;
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
public class AudioPacketClipper implements SyncClockControl, AvUnit {

    private static final Logger sLog = Logger.getLogger( AudioPacketClipper.class.getName() );

    public static final long EMPTY_PACKET_MICROS = 100000L;

    private final MemoryManager mOptMem;

    private final InHandler  mInput  = new InHandler();
    private final OutHandler mOutput = new OutHandler();
    private final FullClock  mClock  = new FullClock( Clock.SYSTEM_CLOCK );

    private boolean mOpen = false;

    private PacketAllocator<DrawPacket> mAlloc;

    private long            mClipMicros = Long.MIN_VALUE;
    private boolean         mForward    = true;
    private DrawPacket      mOutPacket;
    private boolean         mOutIsGap;

    // Used to cut up large packets when mOutIsGap == true (which means mOutPacket has no samples)
    private StreamFormat mOutFormat;
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
            mAlloc = OneFormatAllocator.createPacketLimited( 32 );
        } else {
            mAlloc = mOptMem.allocator( mOutFormat );
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

        final int samps = (int)Frac.multLong( t1 - t0, mOutFormat.mSampleRate, 1000000 );
        mOutPacket = mAlloc.alloc( mOutFormat, samps );
        mOutPacket.init( mOutFormat, t0, t1, false );
        mOutPacket.nbSamples( samps );

        final boolean planar = JavSampleFormat.isPlanar( mOutFormat.mSampleFormat );
        final int     chans  = planar ? mOutFormat.mChannels : 1;
        final int     len    = mOutPacket.lineSize( 0 );

        for( int i = 0; i < chans; i++ ) {
            JavMem.memset( mOutPacket.extendedDataElem( i ), 0, len );
        }

        return true;
    }


    private class InHandler extends InPadAdapter<DrawPacket> {

        private StreamFormat mFormat = null;

        @Override
        public int offer( DrawPacket packet ) {
            if( packet == null ) {
                return OKAY;
            }

            if( mOutPacket != null ) {
                return DRAIN_UNIT;
            }
            mOutIsGap = false;

            if( !packet.isGap() ) {
                mOutIsGap = false;
                if( mFormat == null || !mFormat.matches( packet ) ) {
                    mFormat = StreamFormat.fromAudioPacket( packet );
                }

                if( mForward ) {
                    mOutPacket = clipForward( packet, mOutFormat, mClipMicros, mAlloc );
                } else {
                    mOutPacket = clipBackward( packet, mOutFormat, mClipMicros, mAlloc );
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
            return mOutPacket == null ? OKAY : DRAIN_UNIT;
        }
    }


    private class OutHandler extends OutPadAdapter {

        @Override
        public void config( StreamFormat format ) {
            mOutFormat = format;
        }

        @Override
        public int status() {
            return mOutPacket == null ? FILL_UNIT : OKAY;
        }

        @Override
        public int poll( Refable[] out ) {
            if( mOutPacket == null ) {
                return FILL_UNIT;
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


    public static DrawPacket clipForward( DrawPacket packet,
                                          StreamFormat format,
                                          long clipMicros,
                                          PacketAllocator<DrawPacket> alloc )
    {
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

        DrawPacket ret = alloc.alloc( format, totalSamples );
        final int sampSize = JavSampleFormat.getBytesPerSample( format.mSampleFormat );

        if( writeSamples > 0 ) {
            if( format.mChannels == 1 || !JavSampleFormat.isPlanar( format.mSampleFormat ) ) {
                final int off = (totalSamples - writeSamples) * sampSize * format.mChannels;
                final int len = writeSamples * sampSize * format.mChannels;
                JavMem.copy( packet.dataElem( 0 ) + off, ret.dataElem( 0 ), len );
            } else {
                final int off = (totalSamples - writeSamples) * sampSize;
                final int len = writeSamples * sampSize;
                for( int i = 0; i < format.mChannels; i++ ) {
                    JavMem.copy( packet.extendedDataElem( i ) + off,
                                 ret.extendedDataElem( i ),
                                 len );
                }
            }
        }

        ret.nbSamples( writeSamples );
        ret.init( format, clipMicros, t1, packet.isGap() );
        return ret;
    }


    public static DrawPacket clipBackward( DrawPacket packet,
                                           StreamFormat format,
                                           long clipMicros,
                                           PacketAllocator<DrawPacket> alloc )
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


        final int sampSize = JavSampleFormat.getBytesPerSample( format.mSampleFormat );
        DrawPacket ret = alloc.alloc( format, totalSamples );

        if( writeSamples != 0 ) {
            if( format.mChannels == 1 || !JavSampleFormat.isPlanar( format.mSampleFormat ) ) {
                long src = packet.dataElem( 0 );
                long dst = packet.dataElem( 0 );
                JavMem.copyReverse( src, dst, writeSamples, sampSize * format.mChannels );
            } else {
                for( int i = 0; i < format.mChannels; i++ ) {
                    long src = packet.extendedDataElem( i );
                    long dst = packet.extendedDataElem( i );
                    JavMem.copyReverse( src, dst, writeSamples, sampSize );
                }
            }
        }

        ret.nbSamples( writeSamples );
        ret.init( format, packet.startMicros(), clipMicros, packet.isGap() );
        return ret;
    }

}
