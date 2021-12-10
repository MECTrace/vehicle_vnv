package com.penta.vehiclevnv.domain;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class Count {

    private AtomicInteger success;
    private AtomicInteger fail;
    private AtomicInteger others;

    public Count() {
        this.success = new AtomicInteger(0);
        this.fail = new AtomicInteger(0);
        this.others = new AtomicInteger(0);
    }

    public void countSuccess() {
        this.success.getAndAdd(1);
    }

    public void countFail() {
        this.fail.getAndAdd(1);
    }

    public void countOthers() {
        this.others.getAndAdd(1);
    }


}
