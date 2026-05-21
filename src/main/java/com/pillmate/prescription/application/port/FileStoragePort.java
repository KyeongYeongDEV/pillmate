package com.pillmate.prescription.application.port;

public interface FileStoragePort {
    /** S3 PUT 전용 Pre-signed URL 반환. objectKey는 UUID 기반으로 생성해서 넘긴다. */
    String generatePutUrl(String objectKey);

    /** S3 GET 전용 Pre-signed URL 반환 */
    String generateGetUrl(String objectKey);
}
