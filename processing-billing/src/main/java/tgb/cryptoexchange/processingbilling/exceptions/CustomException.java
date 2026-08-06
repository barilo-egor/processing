package tgb.cryptoexchange.processingbilling.exceptions;

import com.google.rpc.Code;

public interface CustomException {

    Code getErrorCode();

    String getField();

    String getDescription();

}
