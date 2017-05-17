package gov.samhsa.c2s.documentvalidator.service;

import gov.samhsa.c2s.common.validation.XmlValidation;
import gov.samhsa.c2s.common.validation.XmlValidationResult;
import gov.samhsa.c2s.common.validation.exception.XmlDocumentReadFailureException;
import gov.samhsa.c2s.documentvalidator.infrastructure.CcdaValidator;
import gov.samhsa.c2s.documentvalidator.infrastructure.ValidationCriteria;
import gov.samhsa.c2s.documentvalidator.service.dto.DocumentValidationResultDetail;
import gov.samhsa.c2s.documentvalidator.service.dto.DocumentValidationResultSummary;
import gov.samhsa.c2s.documentvalidator.service.dto.ValidationRequestDto;
import gov.samhsa.c2s.documentvalidator.service.dto.ValidationResponseDto;
import gov.samhsa.c2s.documentvalidator.service.exception.UnsupportedDocumentTypeValidationException;
import gov.samhsa.c2s.documentvalidator.service.schema.CCDAVersion;
import gov.samhsa.c2s.documentvalidator.service.schema.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DocumentValidationServiceImpl implements DocumentValidationService {
    private static final Charset DEFAULT_ENCODING = StandardCharsets.UTF_8;

    @Autowired
    private XmlValidation c32SchemaValidator;

    @Autowired
    private DocumentTypeResolver documentTypeResolver;

    @Autowired
    private CcdaValidator ccdaValidator;

    @Override
    public ValidationResponseDto validateDocument(ValidationRequestDto requestDto) {
        //Determine document type
        final DocumentType documentType = documentTypeResolver.resolve(convertByteDocumentToString(requestDto));
        log.info("Identified document as " + documentType);
        ValidationResponseDto responseDto;

        if (DocumentType.HITSP_C32.equals(documentType)) {
            responseDto = runC32Validator(requestDto, documentType);
        } else if (documentType.isCCDA(CCDAVersion.R1) || documentType.isCCDA(CCDAVersion.R2)) {
            responseDto = runCCDAValidator(requestDto, documentType);
        } else {
            throw new UnsupportedDocumentTypeValidationException("Unsupported document type to validate");
        }
        return responseDto;
    }

    private ValidationResponseDto runC32Validator(ValidationRequestDto requestDto, DocumentType documentType) {
        ValidationResponseDto validationResponseDto = new ValidationResponseDto();
        try {
            XmlValidationResult xmlValidationResult = c32SchemaValidator.validateWithAllErrors(convertByteDocumentToString(requestDto));
            //Map xmlValidationResult to DocumentValidationResultDetail
            List<DocumentValidationResultDetail> validatorResults = xmlValidationResult.getExceptions().stream()
                    .map(e -> DocumentValidationResultDetail.builder()
                            .description(e.getMessage())
                            .isSchemaError(true)
                            .documentLineNumber(Integer.toString(e.getLineNumber()))
                            .build())
                    .collect(Collectors.toList());

            validationResponseDto.setValidationResultDetails(validatorResults);
            validationResponseDto.setDocumentType(documentType);
            validationResponseDto.setDocumentValid(xmlValidationResult.isValid());
            validationResponseDto.setValidationResultSummary(DocumentValidationResultSummary.builder()
                    .validationCriteria(ValidationCriteria.C32_SCHEMA_ONLY)
                    .build());
        } catch (XmlDocumentReadFailureException e) {
            log.error(e.getMessage(), e);
        }
        return validationResponseDto;
    }

    private ValidationResponseDto runCCDAValidator(ValidationRequestDto requestDto, DocumentType documentType) {
        List<DocumentValidationResultDetail> validatorResults = ccdaValidator.validateCCDA(convertByteDocumentToString(requestDto));

        ValidationResponseDto validationResponseDto = new ValidationResponseDto();
        validationResponseDto.setDocumentType(documentType);
        validationResponseDto.setValidationResultDetails(validatorResults);
        return validationResponseDto;
    }

    private String convertByteDocumentToString(ValidationRequestDto requestDto) {
        // Set UTF-8 as default encoding if no present
        final Charset charset = requestDto.getDocumentEncoding().map(Charset::forName).orElse(DEFAULT_ENCODING);
        return new String(requestDto.getDocument(), charset);
    }
}