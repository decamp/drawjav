/*
 * Copyright (c) 2016. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

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
import java.util.ArrayList;
import java.util.List;


/**
 * @author Philip DeCamp
 */
public final class AllPlayer implements Channel {

    private final MemoryManager mMem;
    private final PlayClock     mClock;
    private final boolean       mStepping;

    private final PacketReaderUnit   mReader;
    private final SchedulerUnit      mScheduler;
    private final GraphDriver        mDriver;
        
    
    private List<Texture> mTextures = new ArrayList<Texture>();
        

    public AllPlayer( 
            MemoryManager optMem,
            PlayClock optClock,
            PacketReader reader,
            boolean stepping 
    )
            throws IOException
    {
        if( optMem == null ) {
            optMem = new PoolPerFormatMemoryManager( 128, -1 );
        }
    
        mMem = optMem;
        mStepping = stepping;
        if( optClock == null ) {
            optClock = PlayController.createAuto().clock();
        }
        mClock = optClock;

        StreamFormat videoFormat = StreamFormat.createVideo( -1, -1, Jav.AV_PIX_FMT_BGRA, new Rational( 1, 1 ) );
        StreamFormat audioFormat = StreamFormat.createAudio( 1, 48000, Jav.AV_SAMPLE_FMT_FLT );
        
        mReader = new PacketReaderUnit( reader );
        mScheduler = stepping ? new TickerSchedulerUnit() : new ThreadedSchedulerUnit();
        
        AvGraph graph = new AvGraph();
        int maxStream = mReader.outputNum();
        int myStreamCount = 0;
                
        for( int i = 0; i < maxStream; i++ ) {
            Stream stream = reader.stream( i );
            if( !reader.isStreamOpen( stream ) ) {
                continue;
            }
            
            if( stream.format().mType == Jav.AVMEDIA_TYPE_AUDIO ) {
                AudioPacketClipper clipper = new AudioPacketClipper( optMem ); //optMem.audioAllocator( srcStream ) );
                AudioResamplerUnit resampler = new AudioResamplerUnit( optMem );
                SolaUnit sola = new SolaUnit( optMem );
                LineOutUnit lineOut = new LineOutUnit( null );
                
                graph.connect( mReader, mReader.output( i ), clipper, clipper.input( 0 ), stream.format() );
                graph.connect( clipper, clipper.output( 0 ), resampler, resampler.input( 0 ), stream.format() );
                graph.connect( resampler, resampler.output( 0 ), sola, sola.input( 0 ), audioFormat );
                graph.connect( sola, sola.output( 0 ), lineOut, lineOut.input( 0 ), audioFormat );

                myStreamCount++;
                
            } else if( stream.format().mType == Jav.AVMEDIA_TYPE_VIDEO ) {

                VideoResamplerUnit resampler = new VideoResamplerUnit( mMem );
                VideoTextureUnit tex = new VideoTextureUnit();
                
                mScheduler.addStream( mClock, 24 );
                
                graph.connect( mReader, mReader.output( i ), resampler, resampler.input( 0 ), null );
                graph.connect( 
                        resampler, 
                        resampler.output( 0 ), 
                        mScheduler, 
                        mScheduler.input( mScheduler.inputNum() - 1 ), 
                        videoFormat 
                );
                graph.connect( 
                        mScheduler,
                        mScheduler.output( mScheduler.inputNum() - 1 ),
                        tex,
                        tex.input( 0 ),
                        videoFormat
                );
                
                mTextures.add( tex.texture() );
            } 
        }
        
        mDriver = new GraphDriver( mClock, graph );
        if( stepping ) {
            mDriver.addTicker( mScheduler );
        }
    }


    public PlayClock clock() {
        return mClock;
    }

    
    public int textureCount() {
        return mTextures.size();
    }
    

    public Texture texture( int idx ) {
        return mTextures.get( 0 );
    }


    public boolean isStepping() {
        return mStepping;
    }


    public void start() {
        if( !mStepping ) {
            mDriver.startThreadedMode();
        }
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
