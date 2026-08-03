package com.therateam.therateam.controller;

import com.therateam.therateam.model.Modulo;
import com.therateam.therateam.repository.ModuloRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogo fijo de módulos — solo lectura, no administrable (ver Modulo.java). */
@RestController
@RequestMapping("/api/modulos")
@RequiredArgsConstructor
public class ModuloController {
    private final ModuloRepository repository;

    @GetMapping
    public List<Modulo> getAll() { return repository.findAll(); }
}
