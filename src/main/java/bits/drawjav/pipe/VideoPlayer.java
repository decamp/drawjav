package bits.drawjav.pipe;

import bits.draw3d.Texture;
import bits.drawjav.*;
import bits.drawjav.video.VideoTextureUnit;
import bits.jav.Jav;
import bits.jav.util.Rational;
import bits.microtime.PlayClock;
import bits.microtime.PlayController;

import java.io.IOException;
import java.nio.channels.Channel;


/**
 * @author Philip DeCamp
 */
public class VideoPlayer implements Channel {

    private final MemoryManager mMem;
    private final PlayClock     mClock;
    private final boolean       mStepping;

    private final PacketReaderUnit   mReader;
    private final VideoResamplerUnit mResampler;
    private final SchedulerUnit      mScheduler;
    private final VideoTextureUnit   mTexture;
    private final GraphDriver        mDriver;


    public VideoPlayer( MemoryManager optMem,
                        PlayClock optClock,
                        PacketReader reader,
                        boolean stepping )
                        throws IOException
    {
        if( optMem == null ) {
            optMem = new PoolPerFormatMemoryManager( 128, 64 * 1024 * 1024, -1, -1 );
        }
        mMem = optMem;
        mStepping = stepping;
        if( optClock == null ) {
            optClock = PlayController.createAuto().clock();
        }
        mClock = optClock;

        StreamFormat dstFormat = StreamFormat.createVideo( -1, -1, Jav.AV_PIX_FMT_BGRA, new Rational( 1, 1 ) );

        mReader    = new PacketReaderUnit( reader );
        mResampler = new VideoResamplerUnit( mMem );
        mScheduler = stepping ? new TickerSchedulerUnit() : new ThreadedSchedulerUnit();
        mTexture   = new VideoTextureUnit();

        mScheduler.addStream( mClock, 16 );

        AvGraph graph = new AvGraph();
        graph.connect( mReader,    mReader.output( 0 ),    mResampler, mResampler.input( 0 ), null );
        graph.connect( mResampler, mResampler.output( 0 ), mScheduler, mScheduler.input( 0 ), dstFormat );
        graph.connect( mScheduler, mScheduler.output( 0 ), mTexture,   mTexture.input( 0 ),   dstFormat );

        if( !stepping ) {
            mDriver = GraphDriver.createThreaded( mClock, graph );
        } else {
            mDriver = GraphDriver.createStepping( mClock, graph, mScheduler );
        }
    }


    public PlayClock clock() {
        return mClock;
    }


    public Texture texture() {
        return mTexture.texture();
    }


    public boolean isStepping() {
        return mStepping;
    }


    public void start() {
        mDriver.start();
    }


    public void tick() {
        mDriver.tick();
    }

    @Override
    public boolean isOpen() {
        return mDriver.isOpen();
    }

    @Override
    public void close() throws IOException {
        mDriver.close();
    }

}
