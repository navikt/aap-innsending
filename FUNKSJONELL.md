# Innsending
En innsending kan være enten en søknad eller en ettersendelse

## Motta innsending
- søknad har json råformat
- søknad kan sendes med og uten vedlegg
- ettersendelse har ikke json råformat
- ettersendelse kan bestå av fler vedlegg
- innsendinger lagres i PosgreSQL

## Mellomlagre innsending
- søknad mellomlagres før den faktisk sendes inn
- mellomlagring skjer i Redis

```mermaid
graph LR
A((API req)) -- fant PDF --> B((Antivirus))
A((API req)) -- fant IMG --> C((PDF Gen))
A((API req)) -- ingen fil --> D((API res))
A((API req)) -- tom fil --> D((API res))
A((API req)) -- feil content-type --> D((API res))
B((Antivirus)) -- ingen virus --> C((PDF Gen))
B((Antivirus)) -- virus --> D((API res))
C((PDF Gen)) -- generert --> E((PDF Valider))
C((PDF Gen)) -- feilet --> D((API res))
E((PDF Valider)) -- ugyldig --> D((API res))
E((PDF Valider)) -- kryptert --> D((API res))
E((PDF Valider)) -- lesbar/ukryptert --> F((Redis))
F((Redis)) -- lagret --> D((API res))
F((Redis)) -- feilet --> D((API res))
```


## Arkivere innsending
- mottatte innsendinger arkiveres i Joark
- ved arkivering slettes dataene fra postgres og redis
- ved arkivering lagres metadata om hva som ble arkivert i joark

