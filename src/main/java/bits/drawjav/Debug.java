package bits.drawjav;

import bits.util.Dates;
import java.text.DateFormat;
import java.util.*;


/**
 * @author Philip DeCamp
 */
public class Debug {

    private static final TimeZone   ZONE   = Dates.ZONE_US_EASTERN;
    private static final DateFormat FORMAT = Dates.millisecondFormatter( ZONE );

    public static void print( DrawPacket packet ) {
        print( null, packet );
    }


    public static void print( String msg, DrawPacket packet ) {
        if( msg != null ) {
            System.out.print( msg + "  " );
        }

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

}
