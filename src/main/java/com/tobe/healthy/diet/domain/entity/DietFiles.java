package com.tobe.healthy.diet.domain.entity;

import com.tobe.healthy.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
@Table(name = "diet_files")
@Builder
@Getter
public class DietFiles extends BaseTimeEntity<DietFiles, Long> {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "file_id")
    private Long id;

    private String fileName;
    private String originalName;

    @Column(name = "file_ext")
    private String extension;

    private Long fileSize;
    private String fileUrl;

    @ColumnDefault("false")
    @Builder.Default
    private Boolean delYn = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diet_id")
    private Diet diet;

    @Enumerated(STRING)
    @ColumnDefault("'BREAKFAST'")
    private DietType type;

    public static DietFiles create(String savedFileName, String originalName, String extension, long fileSize, Diet diet, String fileUrl, DietType type) {
        return DietFiles.builder()
                .fileName(savedFileName)
                .originalName(originalName)
                .extension(extension)
                .fileSize(fileSize)
                .fileUrl(fileUrl)
                .diet(diet)
                .type(type)
                .build();
    }

    public void deleteDietFile() {
        this.delYn = true;
    }

}