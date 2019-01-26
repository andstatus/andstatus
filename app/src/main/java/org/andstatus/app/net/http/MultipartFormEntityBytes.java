package org.andstatus.app.net.http;

class MultipartFormEntityBytes {
    final String contentTypeName;
    final String contentTypeValue;
    final byte[] bytes;

    MultipartFormEntityBytes(String contentTypeName, String contentTypeValue, byte[] bytes) {
        this.contentTypeName = contentTypeName;
        this.contentTypeValue = contentTypeValue;
        this.bytes = bytes;
    }
}
