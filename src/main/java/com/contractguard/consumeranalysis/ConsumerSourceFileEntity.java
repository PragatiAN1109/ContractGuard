package com.contractguard.consumeranalysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** One Java file belonging to a registered consumer source revision. */
@Entity
@Table(name = "consumer_source_file")
public class ConsumerSourceFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private int position;

    protected ConsumerSourceFileEntity() {
    }

    ConsumerSourceFileEntity(String path, String content, int position) {
        this.path = path;
        this.content = content;
        this.position = position;
    }

    public String getPath() {
        return path;
    }

    public String getContent() {
        return content;
    }

    public int getPosition() {
        return position;
    }
}
