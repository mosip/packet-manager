package io.mosip.commons.packet.exception;

import io.mosip.commons.packet.constants.PacketUtilityErrorCodes;
import io.mosip.kernel.core.exception.BaseUncheckedException;

public class ObjectDoesnotExistsException extends BaseUncheckedException {

    public ObjectDoesnotExistsException() {
        super(PacketUtilityErrorCodes.OBJECT_DOESNOT_EXISTS.getErrorCode(),
                PacketUtilityErrorCodes.OBJECT_DOESNOT_EXISTS.getErrorMessage());
    }

    public ObjectDoesnotExistsException(String message) {
        super(PacketUtilityErrorCodes.OBJECT_DOESNOT_EXISTS.getErrorCode(),
                message);
    }

    public ObjectDoesnotExistsException(Throwable e) {
        super(PacketUtilityErrorCodes.OBJECT_DOESNOT_EXISTS.getErrorCode(),
                PacketUtilityErrorCodes.OBJECT_DOESNOT_EXISTS.getErrorMessage(), e);
    }

    public ObjectDoesnotExistsException(String errorMessage, Throwable t) {
        super(PacketUtilityErrorCodes.OBJECT_DOESNOT_EXISTS.getErrorCode(), errorMessage, t);
    }
}
