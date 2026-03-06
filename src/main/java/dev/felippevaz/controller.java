package dev.felippevaz;

import dev.felippevaz.annotations.Controller;
import dev.felippevaz.annotations.Get;

@Controller("/test")
public class controller {

    @Get
    public void get() {
        System.out.println("PASSOU");
    }
}
