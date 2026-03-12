package io.mosip.commons.packet.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode
public class Packet implements Serializable {

    private PacketInfo packetInfo;
    private byte[] packet;
}
