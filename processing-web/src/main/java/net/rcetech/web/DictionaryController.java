package net.rcetech.web;

import net.rcetech.meta.DictionaryField;
import net.rcetech.meta.WebPath;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(WebPath.PRIVATE_API_PATH + "/dictionary")
public class DictionaryController {

    private final Map<String, List<Map<String, Object>>> dictionary;

    public DictionaryController(List<DictionaryField> dictionaryFields) {
        this.dictionary = new HashMap<>();
        for (DictionaryField dictionaryField : dictionaryFields) {
            this.dictionary.put(dictionaryField.getField(), dictionaryField.getContent());
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getDictionary() {
        return new ResponseEntity<>(dictionary, HttpStatus.OK);
    }
}
