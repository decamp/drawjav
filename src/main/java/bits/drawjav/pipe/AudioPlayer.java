package bits.drawjav.pipe;

import bits.drawjav.*;
import bits.drawjav.audio.AudioFormat;
import bits.jav.Jav;
import bits.microtime.*;

import java.io.IOException;
import java.nio.channels.Channel;


/**
 * @author Philip DeCamp
 */
public class AudioPlayer implements Channel {

    private final MemoryManager mMem;
    private final PlayClock     mClock;

    private final ReaderFilter       mReader;
    private final ResamplerFilter    mResampler;
    private final AudioPacketClipper mClipper;
    private final SolaFilter         mSola;
    private final LineOutFilter      mLineOut;

    private final ClockEventQueue mEvents = new ClockEventQueue( 2048 );

    private final FilterGraph mGraph = new FilterGraph();
    private final Object      mLock  = mGraph;

    private volatile boolean vOpen = true;


    public AudioPlayer( MemoryManager optMem,
                        PlayClock optClock,
                        PacketReader reader )
                        throws IOException
    {
        if( optMem == null ) {
            optMem = new PoolPerFormatMemoryManager( 128, 64 * 1024 * 1024, -1, -1 );
        }
        mMem = optMem;
        if( optClock == null ) {
            optClock = PlayController.createAuto().clock();
        }
        mClock = optClock;

        AudioFormat  srcFormat = new AudioFormat( 1, 48000, Jav.AV_SAMPLE_FMT_S16P );
        AudioFormat  dstFormat = new AudioFormat( 1, 48000, Jav.AV_SAMPLE_FMT_FLT );
        StreamHandle srcStream = new BasicStreamHandle( Jav.AVMEDIA_TYPE_AUDIO, null, srcFormat );
        StreamHandle dstStream = new BasicStreamHandle( Jav.AVMEDIA_TYPE_AUDIO, null, dstFormat );

        mReader    = new ReaderFilter( reader );
        mClipper   = new AudioPacketClipper( optMem ); //optMem.audioAllocator( srcStream ) );
        mResampler = new ResamplerFilter( optMem );
        mSola      = new SolaFilter( optMem );
        mLineOut   = new LineOutFilter( null );

        mGraph.connect( mReader, mReader.output( 0 ), mClipper, mClipper.input( 0 ), srcStream );
        mGraph.connect( mClipper, mClipper.output( 0 ), mResampler, mResampler.input( 0 ), srcStream );
//        mGraph.connect( mReader, mReader.output( 0 ), mResampler, mResampler.input( 0 ), srcStream );
        mGraph.connect( mResampler, mResampler.output( 0 ), mSola, mSola.input( 0 ), dstStream );
        mGraph.connect( mSola, mSola.output( 0 ), mLineOut, mLineOut.input( 0 ), dstStream );
//        mGraph.connect( mResampler, mResampler.output( 0 ), mLineOut, mLineOut.input( 0 ), dstStream );

        synchronized( mClock ) {
            mClock.addListener( mEvents );
            mClock.addListener( mLineOut );
            mClock.applyTo( mEvents );
            mClock.applyTo( mLineOut );
        }

        new Thread( "AudioPlayer" ) {
            public void run() {
                runLoop();
            }
        }.start();
    }


    public PlayClock clock() {
        return mClock;
    }

    @Override
    public boolean isOpen() {
        return vOpen;
    }

    @Override
    public void close() throws IOException {
        synchronized( mLock ) {
            if( !vOpen ) {
                return;
            }
            vOpen = false;
            mLock.notifyAll();
        }
    }


    private void runLoop() {
        while( true ) {
            synchronized( mLock ) {
                if( !vOpen ) {
                    return;
                }

                // Process events.
                while( true ) {
                    ClockEvent e = mEvents.poll();
                    if( e == null ) {
                        break;
                    }

                    switch( e.mId ) {
                    case ClockEvent.CLOCK_RATE: {
                        ClockEvent seek;
                        synchronized( mClock ) {
                            seek = ClockEvent.createClockSeek( this, mClock.masterBasis(), mClock.timeBasis() );
                        }
                        mGraph.postEvent( e );
                        mGraph.postEvent( seek );
                        mGraph.clear();
                        break;
                    }

                    case ClockEvent.CLOCK_SEEK:
                        mGraph.postEvent( e );
                        mGraph.clear();
                        break;

                    default:
                        mGraph.postEvent( e );
                    }
                }
            }

            switch( mGraph.step() ) {
            case FilterGraph.WAIT:
            case FilterGraph.FINISHED:
                mGraph.waitForWork( 1000L );
                break;
            }
        }
    }

}
