package com.retailflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchCommitRequest(@NotEmpty @Size(max = 500) List<@Valid BatchItem> items) {}
