package bits.drawjav;

import bits.util.ref.Refable;


/**
 * @author Philip DeCamp
 */
public interface PacketAllocator<T extends Refable> extends Refable {
    /**
     * @param format Stream the packet will be used in, with accurate format information.
     * @param size   Additional size parameter. <br>
     *               For audio packet: the number of samples per channel <br>
     *               For video packet: unused <br>
     *               For data packets: number of bytes <br>
     * @return Writable packet.
     */
    public T alloc( StreamFormat format, int size );
}
