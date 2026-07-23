# Compatibility spike старого Word DOC

## Решение

Поддержка бинарного Word 97–2003 `.doc` в этой версии не включена.
`application/msword` не зарегистрирован в manifest и внутреннем picker, а каталог не заявляет
DOC. Стабильный DOCX viewer от этого результата не зависит.

## Apache POI HWPF 5.5.1

Проверялась официальная актуальная release-линия Apache POI:

- [Apache POI 5.5.1 downloads](https://poi.apache.org/download.cgi);
- [component map: HWPF находится в poi-scratchpad, POIFS — в poi](https://poi.apache.org/components/);
- [официальное описание HWPF](https://poi.apache.org/components/document/index.html).

Временный spike добавлял `org.apache.poi:poi-scratchpad:5.5.1`, затем dependency и probe были
полностью удалены из production tree.

Resolved transitive JAR:

| Artifact | Размер JAR, bytes |
|---|---:|
| `poi-scratchpad-5.5.1` | 1 913 107 |
| `poi-5.5.1` | 3 006 527 |
| `commons-codec-1.20.0` | 401 021 |
| `commons-collections4-4.5.0` | 898 652 |
| `commons-math3-3.6.1` | 2 213 560 |
| `commons-io-2.21.0` | 585 274 |
| `SparseBitSet-1.3` | 25 843 |
| `log4j-api-2.24.3` | 348 513 |
| **Всего до D8/R8** | **9 392 497** |

Лицензия самого POI — Apache License 2.0, но production dependency не оставлена, поэтому
нового bundled license/NOTICE для release нет.

### D8

Команда:

```powershell
.\gradlew.bat assembleDebug --no-daemon --no-configuration-cache
```

Результат при `minSdk 24`: failure в `mergeExtDexDebug`.

```text
MethodHandle.invoke and MethodHandle.invokeExact are only supported starting
with Android O (--min-api 26):
org/apache/poi/poifs/nio/CleanerUtil->lambda$null$0(...)
```

`javap -c -p` подтвердил реальный bytecode call
`java.lang.invoke.MethodHandle.invokeExact(ByteBuffer)` и desktop-oriented unmap path через
`sun.misc.Unsafe`. Повышение `minSdk` запрещено рамками продукта.

### R8 и desktop API

Release build без reachable HWPF вызова формально проходил, потому что R8 полностью удалял
неиспользуемые POI/HWPF classes. Поэтому был добавлен временный retained probe:
`new HWPFDocument(InputStream).getRange().text()`.

С reachable entry point `minifyReleaseWithR8` завершился failure. Помимо optional annotation,
OSGi и logging types, R8 обнаружил отсутствующие Android classes:

- `java.awt.Color`, `Dimension`, `Rectangle`;
- `java.awt.geom.*`;
- `java.awt.image.*`.

`jdeps --multi-release base` также показывает dependency `poi-scratchpad` на module
`java.desktop`. Простое подключение HWPF/POIFS как официальных Maven artifacts поэтому не
проходит одновременно debug D8 и release R8. Ручное вырезание/пересборка классов означало бы
поддержку собственного POI fork и не является допустимым production-решением этого spike.

Поскольку Android artifact не собирается, реальные WordDocument/0Table/1Table и encrypted
DOC fixtures невозможно было честно прогнать в приложении. Добавлять MIME после JVM-only
демонстрации было бы ложным заявлением поддержки.

## Оценка собственного text-first parser

Минимальная безопасная реализация должна следовать двум отдельным спецификациям:

- [MS-CFB header](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-cfb/05060311-bfce-4b12-874d-71fd4ce63aea),
  [FAT](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-cfb/30e1013a-a0ff-4404-9ccf-d75d835ff404),
  [MiniFAT](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-cfb/c5d235f7-b73c-4ec5-bf8d-5c08306cd023)
  и directory/stream chains;
- [MS-DOC FibBase](https://learn.microsoft.com/en-us/openspecs/office_file_formats/ms-doc/26fb6c06-4e5c-4778-ab4e-edbf26a545bb),
  [text retrieval через CLX/PlcPcd](https://learn.microsoft.com/es-es/openspecs/office_file_formats/ms-doc/01d5d8c4-cf9c-4ef9-80fd-439e763cfe01)
  и [encryption flags/modes](https://learn.microsoft.com/en-us/openspecs/office_file_formats/ms-doc/37639397-6451-427b-9cf2-01d56e927f25).

Нужны:

1. bounded OLE signature/header parser с проверкой sector shifts и file bounds;
2. cycle/duplicate/bounds-safe DIFAT, FAT и MiniFAT chain traversal;
3. bounded directory tree и чтение normal/mini streams;
4. обязательный `WordDocument` и выбранный `0Table`/`1Table`;
5. FIB version/size validation, `fWhichTblStm` и ранний понятный reject `fEncrypted`;
6. bounded `fcClx/lcbClx`, Pcdt/PlcPcd, строго возрастающие CP и checked CP→FC arithmetic;
7. ANSI/codepage compressed pieces и little-endian UTF-16 pieces;
8. фильтрация control markers и plain-text block model без обещания formatting fidelity;
9. synthetic fixtures для RU/EN, обоих table streams, multiple/compressed/Unicode pieces,
   MiniFAT, corruption, cycles, truncation, overflow и encryption;
10. fuzzing, unit limits и physical API 24/current Android smoke test.

Это не небольшой extension resolver: ошибки в chain arithmetic или piece table дают
неконтролируемые allocations, loops и чтение неверных offsets. В рамках этого изменения
прототип без полного corpus/security hardening не создавался, чтобы его нельзя было случайно
выдать за рабочую поддержку DOC.

## Условия для будущего включения

DOC можно добавить только когда одновременно:

- debug D8 и release R8 проходят на неизменном `minSdk 24`;
- нет runtime dependency на `java.awt`, desktop-only API или скрытые Android API;
- реальные и synthetic WordDocument/0Table/1Table fixtures извлекают RU/EN text;
- compressed/Unicode и multiple pieces проходят;
- corrupt, missing-table, fake OLE и encrypted cases дают bounded user-facing error;
- APK delta и лицензии приняты;
- physical smoke test выполнен.

До этого `application/msword` отсутствует, и продукт заявляет только DOCX.
