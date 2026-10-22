package net.rcetech.api.controller;

import net.rcetech.api.merchantdetails.ApiMerchantDetailsGrpcService;
import net.rcetech.api.merchantdetails.MerchantCallbackDTO;
import net.rcetech.meta.Profiles;
import net.rcetech.meta.WebPath;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(WebPath.PUBLIC_API_PATH + "/merchant-callback")
@Profile(Profiles.TEST_MERCHANT_CALLBACK)
public class MerchantTestCallbackController {

    private final ApiMerchantDetailsGrpcService merchantDetailsGrpcService;

    public MerchantTestCallbackController(ApiMerchantDetailsGrpcService merchantDetailsGrpcService) {
        this.merchantDetailsGrpcService = merchantDetailsGrpcService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public void send(@RequestBody MerchantCallbackDTO merchantCallbackDTO) {
        merchantDetailsGrpcService.getCallback(merchantCallbackDTO);
    }
}
