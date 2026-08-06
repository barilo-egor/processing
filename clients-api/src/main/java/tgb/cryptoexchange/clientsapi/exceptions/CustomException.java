package tgb.cryptoexchange.clientsapi.exceptions;

import com.google.rpc.Code;

public interface CustomException {

    Code getErrorCode();

    String getField();

    String getDescription();

}
