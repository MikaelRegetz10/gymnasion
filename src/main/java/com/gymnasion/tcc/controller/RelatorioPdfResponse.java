package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.dto.RelatorioPdfDTO;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * Monta a resposta de download do relatório em PDF (US-16 C1).
 * O Content-Type é definido na resposta, e não em "produces", para que os erros continuem sendo devolvidos como JSON.
 */
final class RelatorioPdfResponse {

    private RelatorioPdfResponse() {
    }

    static ResponseEntity<byte[]> of(RelatorioPdfDTO relatorio) {
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(relatorio.nomeArquivo(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                // LGPD: o relatório contém dados pessoais e não deve ficar em cache
                .cacheControl(CacheControl.noStore())
                .body(relatorio.conteudo());
    }
}
