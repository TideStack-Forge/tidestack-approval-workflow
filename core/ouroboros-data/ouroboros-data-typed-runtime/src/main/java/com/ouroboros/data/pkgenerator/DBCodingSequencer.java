package com.ouroboros.data.pkgenerator;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import io.vavr.control.Either;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.pkgenerator.model.CodingSequence;

public class DBCodingSequencer implements CodingSequencer {
  @Autowired
  DBCodingSequencer self;

  private final TypedDataModel<String, CodingSequence> dataModel;

  public DBCodingSequencer(TypedDataModel<String, CodingSequence> dataModel) {
    this.dataModel = dataModel;
  }

  @Override
  public Long next(String name) {
    return self.next(name, 1L, 1L);
  }

  @Override
  public Long next(String name, Long step) {
    return self.next(name, step, 1L);
  }

  @Override
  @Transactional
  public Long next(String name, Long step, Long start) {
    var model = getDataModel();
    return model.get(name)
        .toEither()
        .filter(Objects::nonNull)
        .map(Either::get)
        .map(cs -> cs.getLastNumber() + step)
        .peek(next -> {
          var update = new CodingSequence();
          update.setName(name);
          update.setLastNumber(next);
          update.setLastGenerateTime(LocalDateTime.now());
          model.update(name, update);
        })
        .onEmpty(() -> {
          var insert = new CodingSequence();
          insert.setName(name);
          insert.setLastNumber(start);
          insert.setLastGenerateTime(LocalDateTime.now());
          model.insert(insert);
        })
        .getOrElse(start);
  }

  @Override
  public Long peek(String name) {
    return peek(name, 1L, 1L);
  }

  @Override
  public Long peek(String name, Long step) {
    return peek(name, step, 1L);
  }

  @Override
  public Long peek(String name, Long step, Long start) {
    var model = getDataModel();
    return model.get(name)
        .toEither()
        .filter(Objects::nonNull)
        .map(Either::get)
        .map(cs -> cs.getLastNumber() + step)
        .getOrElse(start);
  }

  public TypedDataModel<String, CodingSequence> getDataModel() {
    return dataModel;
  }
}
