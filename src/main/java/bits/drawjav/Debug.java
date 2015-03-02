package bits.drawjav;

import bits.drawjav.DrawPacket;
import bits.drawjav.audio.WavWriter;
import bits.util.Dates;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.*;


/**
 * @author Philip DeCamp
 */
public class Debug {

    private static final TimeZone   ZONE   = Dates.ZONE_US_EASTERN;
    private static final DateFormat FORMAT = Dates.millisecondFormatter( ZONE );

    public static void print( DrawPacket packet ) {
        if( packet == null ) {
            System.out.println( "<null>" );
        }
        System.out.format( "%s -> %s    %s  %d\n",
                           formatTime( packet.startMicros() ),
                           formatTime( packet.stopMicros() ),
                           packet.isGap(),
                           packet.refCount() );
    }


    public static String formatTime( long t ) {
        return FORMAT.format( new Date( t / 1000L ) );
    }


    public static String formatRange( long start, long stop ) {
        return formatTime( start ) + " -> " + formatTime( stop );
    }


    private static List<WavWriter> writers = new ArrayList<WavWriter>();


    public static WavWriter createDebugWriter( File out, int sampleRate ) throws IOException {
        final WavWriter ret = new WavWriter( out, 1, sampleRate );
        writers.add( ret );

        Runtime.getRuntime().addShutdownHook( new Thread() {
            @Override
            public void run() {
                try {
                    ret.close();
                } catch( Exception e ) {
                    e.printStackTrace();
                }
            }
        } );

        return ret;
    }


    public static void closeDebugWriters() {
        for( WavWriter w: writers ) {
            try {
                w.close();
            } catch( IOException e ) {
                e.printStackTrace();
            }
        }
        writers.clear();
    }

}
