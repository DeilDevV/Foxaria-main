import {
  ArrowRight,
  Boxes,
  CheckCircle2,
  Download,
  FileCog,
  FolderGit2,
  FolderTree,
  GitBranch,
  Globe,
  Package,
  Server,
  ShieldAlert,
  TerminalSquare,
  Wrench,
} from "lucide-react";
import Link from "next/link";

/* ------------------------------------------------------------------ */
/*  Данные                                                             */
/* ------------------------------------------------------------------ */

const STATS = [
  { icon: Boxes, value: "26", label: "Gradle-модулей → modules/" },
  { icon: Server, value: "5", label: "серверов → servers/" },
  { icon: GitBranch, value: "735", label: "git mv — история сохранена" },
  { icon: FileCog, value: "156", label: "файлов обновлено: код, конфиги, доки" },
];

const BEFORE_TREE = `Foxaria-main/
├─ foxaria-api/    foxaria-core/    foxaria-economy/
│  foxaria-kits/   foxaria-cases/   foxaria-shop/   … ×26
│  ┄ все модули вперемешку в корне ┄
├─ test-server/            ← сервер
├─ lobby-server/           ← сервер
├─ auth-server/            ← сервер
├─ proxy-bungeecord/       ← сервер
├─ production-server-template/
├─ SITE-FOXARIA/           ← сайт рядом с серверами
├─ modules/quests.yml      ← папка-сирота, 1 файл
├─ ecosystem.config.js     ← pm2 валяется в корне
├─ deploy.sh  up.cmd  deploy/  scripts/  docs/
└─ .playwright-mcp/        ← мусор`;

const AFTER_TREE = `Foxaria-main/
├─ modules/                ★ весь исходный код (Gradle)
│  ├─ foxaria-api/  foxaria-core/  foxaria-bootstrap/
│  ├─ foxaria-proxy-bungee/  foxaria-hub-guard/
│  └─ …26 модулей, имена :foxaria-* прежние
├─ servers/                ★ все рантайм-инстансы
│  ├─ auth/       лобби-аутентификация (25566)
│  ├─ lobby/      лобби (25567)
│  ├─ game/       игровой ← test-server (25568)
│  ├─ proxy/      BungeeCord ← proxy-bungeecord
│  └─ production-template/
├─ web/site/               ★ сайт ← SITE-FOXARIA
├─ config/gameplay/quests.yml
├─ deploy/  pm2 + deploy.sh/ps1 + nginx
├─ scripts/ · docs/
├─ up.cmd · gradlew · build.gradle · settings.gradle
└─ .playwright-mcp — удалён`;

const MOVES: { group: string; icon: typeof Server; rows: [string, string, string?][] }[] = [
  {
    group: "Исходный код",
    icon: Package,
    rows: [
      ["foxaria-* (26 шт.)", "modules/foxaria-*", "имена проектов Gradle не изменились"],
    ],
  },
  {
    group: "Игровые серверы",
    icon: Server,
    rows: [
      ["test-server/", "servers/game/", "игровой Paper-бэкенд"],
      ["lobby-server/", "servers/lobby/", "лобби"],
      ["auth-server/", "servers/auth/", "аутентификация"],
      ["proxy-bungeecord/", "servers/proxy/", "вход для игроков, 25565"],
      ["production-server-template/", "servers/production-template/", "шаблон прода"],
    ],
  },
  {
    group: "Веб и инфраструктура",
    icon: Globe,
    rows: [
      ["SITE-FOXARIA/", "web/site/", "backend + frontend сайта"],
      ["ecosystem.config.js", "deploy/", "pm2-конфиг"],
      ["deploy.sh", "deploy/deploy.sh", "переписан под /opt/foxaria"],
      ["modules/quests.yml", "config/gameplay/quests.yml", "папка-сирота расформирована"],
      [".playwright-mcp/", "удалён", "артефакты скриншотов"],
    ],
  },
];

const FIXES = [
  {
    title: "Кузница: 339 строк вместо 1053 и 8 вкладок",
    text: "Старый верстак был лабиринтом: Главная → Редактор → Бой → Чары → Зелья → Эффекты удара, и после каждого действия меню закрывалось. Новый экран /itpl forge — один экран, где предмет из руки виден прямо в меню и обновляется после каждого клика. Меню не закрывается при редактировании вообще: включение способности, настройка чисел, смена редкости, сброс — всё на месте. Способности настраиваются прямо на иконке: ЛКМ вкл/выкл, ПКМ шанс ±5%, Q сила ±1, двойной клик длительность. Старый верстак остался на /itpl workbench для тонких настроек.",
  },
  {
    title: "Дублирование строк описания — причина найдена",
    text: "Авто-строки помечались в тексте маркерами ⟨Foxaria⟩…⟨/Foxaria⟩ и вырезались при обновлении. Если закрывающий маркер терялся (обрезка лора, ручная правка, старый предмет) или блоков накапливалось несколько — вырезался только один, остальные копились с каждым действием. Теперь свои строки хранятся отдельно в PDC, а описание всегда собирается заново целиком: редкость → строки → способности → рамка. Дубли невозможны в принципе, старые предметы мигрируют автоматически.",
  },
  {
    title: "21 особая способность с настройкой",
    text: "Ближний бой: взрывной удар, ударная волна, вампиризм, добивание. Лук: самонаведение (стрела доворачивает по дуге), разрывная, веерный выстрел, стрела-телепорт. Любая атака: обморожение, молния, цепная молния, иссушение, яд, ослепление, испепеление. Защита: шипы возмездия, второе дыхание, уклонение. Пассив: лёгкость, ночной охотник, аура силы. У каждой настраиваются шанс, сила и длительность. Взрывы идут с breakBlocks = false — постройки не страдают, а задача самонаведения жёстко ограничена по времени.",
  },
  {
    title: "Редкости с подсветкой предметов",
    text: "Шесть уровней от Обычного до Мифического. Каждая красит бейдж, рамку и акценты в описании, так что особый предмет сразу отличается от ванильного — удобно для донат-магазина и кейсов. Есть авто-подбор редкости по числу способностей.",
  },
  {
    title: "Приват: владелец не получал уведомлений",
    text: "Рассылка «в приват вошёл чужак» шла по списку fx_region_members. Владелец туда попадает при создании, но если его строки не было (старые приваты, правки БД) — он единственный оставался без уведомления, хотя участники получали. Теперь владелец добавляется к получателям явно, сам нарушитель из рассылки исключён, а владелец больше не считается «чужаком» в своём же привате.",
  },
  {
    title: "Приваты: 13 блокировок главного потока",
    text: "isMember() дёргался через .join() из событий движения, боя, взаимодействия с блоками, HUD и партиклов — каждый шаг игрока внутри привата выполнял SQL-запрос и вешал главный поток. Добавлен кэш состава с инвалидацией и прогревом на старте: в горячих путях блокирующих запросов больше нет. Заодно карта кулдаунов уведомлений перестала расти бесконечно.",
  },
  {
    title: "Вайп-блокировка: можно было зайти на закрытый сервер",
    text: "Проверка wipeLocked стояла внутри ветки «сессия валидна» — игрок, логинящийся уже во время вайпа, шёл другой веткой и проверку не проходил. Вынес проверку до ветвления. Плюс если лобби недоступно, кик просто не срабатывал и игроки оставались на закрытом сервере — теперь их отключает с причиной. Auth и lobby не блокируются, админ проходит всегда.",
  },
  {
    title: "Новый раздел ВАЙП с защитой от случайного клика",
    text: "Семь отдельных кнопок: приваты, гильдии, аукцион, монеты, лавки, дома, прогресс. Красная рамка и предупреждение — раздел визуально не похож на обычные панели. Двойное подтверждение (второй клик в течение 10 сек), кулдаун с остатком на кнопке, защита от параллельного запуска, транзакция с откатом. Под каждой кнопкой — дата, время, ник админа и число затронутых записей; история переживает рестарт. Донат, баны, аккаунты и аудит не трогаются: монеты обнуляются UPDATE'ом, колонка токенов в той же таблице остаётся нетронутой.",
  },
  {
    title: "RTP высаживал в лаву и под воду",
    text: "Проверка точки шла через isPassable(), а он возвращает true для воды И для лавы — игрока могло закинуть прямо в лавовое озеро. Теперь точка отбраковывается при жидкости или опасном блоке под ногами/головой, при мягкой земле (листва, слизь, магма), при лаве вплотную сбоку или над головой, рядом с чужой постройкой и рядом с приватом. Заодно rtp.min-distance наконец читается — раньше параметр лежал в конфиге, но нигде не использовался, и заброс мог высадить вплотную к 0,0.",
  },
  {
    title: "Фризы и вылеты при телепорте: шесть причин",
    text: "Главная — значение rtp.max-attempts из конфига принудительно поднималось до 100 попыток для первого входа: на новой карте это до сотни генераций чанков подряд. Плюс пул точек после каждой находки вызывал себя мгновенно (шторм генерации при старте), поиск без обработчика ошибок висел вечно, рефлексия приватов дёргала getMethod тысячи раз за один RTP, а guard мог заморозить игрока навсегда. Добавлен лимит генерации чанков, пауза между заполнениями пула, таймауты и кэш рефлексии.",
  },
  {
    title: "Спавн вырезан, заброс — строго один раз",
    text: "Удалены /spawn, /setspawn, их права и методы, отключена spawn-protection, убран телепорт в world spawn после войны гильдий. Случайный заброс теперь честно один раз: отметка ставится ДО телепорта (обрыв связи в полёте не даёт повтор), запись на диск асинхронная с откатом при ошибке, состояние чистится на выходе. Перезаход без смерти — игрок остаётся где вышел, новый заброс только после смерти.",
  },
  {
    title: "Единый стиль всех 31 меню",
    text: "Заголовки были в семи разных форматах: «&6&lАукцион &7| &fлоты», «&8Ядро привата», «&eПросмотр: …». Свёл всё к одному виду ⟨ Название │ подраздел ⟩ и вынес общие элементы в новый класс MenuStyle: заголовки, разделители, подсказки ЛКМ/ПКМ и кнопки Назад/Закрыть/Обновить. Цвета закреплены за разделами — оранжевый экономика, розовый донат, голубой социальное, фиолетовый прогресс.",
  },
  {
    title: "Главное меню /menu переписано с нуля",
    text: "Половина слотов пустовала («воздух под будущие пункты»), а кнопка «Донат» внизу дублировала «Донат-магазин» и просто писала текст в чат. Теперь 20 разделов по смысловым рядам без единой дыры, и добавлено то, чего в меню не было вовсе: кейсы, голосование, рефералы, сезон, заявки TPA и жалобы. У каждой кнопки описание, разделитель и строка команды, в центре — живой профиль с монетами, токенами, рангом и знаниями.",
  },
  {
    title: "Довёл «голые» экраны: кейсы, магазин, наборы",
    text: "Пять меню были без рамки, описаний и кнопок выхода. Кейсы теперь показывают число наград, нужный ключ и шанс выпадения (считается от весов). В магазине цена больше не впихнута в название предмета — название отдельно, покупка/продажа и подсказки в описании. Везде появились «Назад» и «Закрыть».",
  },
  {
    title: "Ловля падения: Material.SPARKLER не существует",
    text: "В меню использовался материал SPARKLER — его нет в Bukkit, это предмет из Bedrock Edition. Открытие раздела роняло бы обработчик меню. Заменено на валидный TRIPWIRE_HOOK, заодно прогнал проверку всех материалов в изменённых файлах.",
  },
  {
    title: "Вход: сессия есть, а в лобби не пускало",
    text: "Корень бага — pendingConnect не очищался при выходе игрока: застрявшая запись срабатывала как «уже подключаемся» и глушила переброс при следующем заходе. Плюс переброс зависел только от authenticatedNow (после рестарта прокси набор пуст) и был «выстрелил и забыл». Теперь: очистка при выходе, фолбэк на проверку сессии и переброс с контролем результата и повторами.",
  },
  {
    title: "Единый стиль: префикс █ FOXARIA в чате",
    text: "Все системные сообщения идут через MessageService — туда добавлен единый градиентный префикс (messages.yml → prefix). Новый FoxariaColors понимает &#RRGGBB и <gradient:#A:#B>, без зависимости от net.md_5.bungee. В GUI префикс не подставляется, чтобы не ломать вёрстку меню.",
  },
  {
    title: "Порядок в меню: админка, модерка, донат",
    text: "Админ-панель была почти копией модер-панели (Жалобы/Проверки/Онлайн) без экономики и доната. Переделана: Донат · Экономика и ранги · Модерация (ссылка, без дублей) · Сервер · Справочник команд · Журнал. Из модер-меню убран лишний доступ: открывался живой инвентарь цели (можно было забрать вещи) и эндер-сундук — теперь снимок только для чтения.",
  },
  {
    title: "Донат управляется с сервера, а не только с сайта",
    text: "Новая команда /fdonate (info · tokens · take · rank [дни] · unrank) и раздел «Донат» в админ-панели с быстрыми ±100 токенов и сводкой — всё пишется в аудит. Плюс справочник всех команд прямо в GUI, чтобы не держать список в голове.",
  },
  {
    title: "Табличка справа: цвет доната не менялся",
    text: "colorize() в сайдбаре понимал только &a-коды, а префиксы донат-рангов приходят с прокси в hex — цвет просто не применялся. Исправлено. Заодно донатер больше не видит «Игрок» первые секунды после входа: если префикс с прокси не доехал, берётся локальный из ranks.yml.",
  },
  {
    title: "Слипперы переписаны по эталону Rust-like тел",
    text: "Тело игрока лежит с реальным скином и не исчезает после рестарта: стабильный entity id на владельца, снапшот скина для поздно зашедших, поза SWIMMING (без глитчей SLEEPING), броня и оффхенд больше не теряются при возврате, удары по телу не долбят БД (троттлинг 2 с), дюп-гонка при лутании закрыта.",
  },
  {
    title: "Запуск с ПК в один клик",
    text: "start.cmd — поднять всё без пересборки, stop.cmd — погасить все 4 сервера, up.cmd — полная сборка+запуск. В конфигах остались только метки [VM]/[!ХОСТИНГ] — ровно те места, что меняешь при переезде на виртуалку. Плюс docs/START-LOCAL.md и docs/SETUP-VM.md.",
  },
  {
    title: "Битые пути к собранным jar",
    text: "up.cmd, update-foxaria.cmd и deploy.sh искали jar в foxaria-bootstrap\\build\\libs — но buildDir давно вынесен в .gradle-build. Деплой молча копировал несуществующие файлы. Теперь пути .gradle-build/<модуль>/libs.",
  },
  {
    title: "Чужие пути к JDK в gradle.properties",
    text: "org.gradle.java.home указывал на E:/Foxaria-main/.jdks конкретной машины — на любом другом ПК/сервере сборка падала. Пути убраны, оставлен закомментированный пример.",
  },
  {
    title: "Отсутствующие start.sh для game и proxy",
    text: "pm2-ecosystem ссылался на /opt/foxaria/test-server/start.sh и proxy start.sh, которых не существовало в репозитории. Добавлены servers/game/start.sh и servers/proxy/start.sh.",
  },
  {
    title: "Хрупкие относительные пути между серверами",
    text: "HubGuard читал базу прокси через ../proxy-bungeecord/... из папок lobby/auth. Все такие ссылки (в коде и yml) переписаны на servers/proxy — логика поиска файла не тронута.",
  },
  {
    title: "Сайт искал БД по старым путям",
    text: "web/site/config.js считал корень репо на один уровень выше себя. Обновлены REPO_ROOT и пути к foxaria.db / foxaria-proxy.db через servers/game и servers/proxy.",
  },
  {
    title: "EOL для батников",
    text: "В .gitattributes добавлено *.bat/*.cmd → CRLF. Без этого git мог отдать LF-файл и сломать goto/метки в cmd.exe.",
  },
];

const APPLY_STEPS = [
  {
    cmd: "Распакуй Foxaria-restructure-kit.zip в любое место",
    hint: "внутри: apply-restructure.ps1 / .sh, files/ (156 файлов), README",
  },
  {
    cmd: "powershell -ExecutionPolicy Bypass -File .\\apply-restructure.ps1",
    hint: "запускать ИЗ КОРНЯ репозитория Foxaria-main — всё через git mv",
  },
  {
    cmd: "gradlew.bat :foxaria-bootstrap:shadowJar   &&   up.cmd",
    hint: "проверка: сборка + перезапуск auth, lobby, game, proxy",
  },
];

/* ------------------------------------------------------------------ */
/*  Мелкие компоненты                                                  */
/* ------------------------------------------------------------------ */

function TreePanel({
  title,
  tone,
  code,
}: {
  title: string;
  tone: "dim" | "accent";
  code: string;
}) {
  const accent = tone === "accent";
  return (
    <div
      className={`rounded-2xl border p-5 font-mono2 text-[12.5px] leading-[1.65] transition-colors sm:p-6 sm:text-[13px] ${
        accent
          ? "glow-fox border-amber-500/25 bg-[#141109]/80"
          : "border-[#1e2420] bg-[#101311]/80 opacity-80"
      }`}
    >
      <div className="mb-4 flex items-center gap-2.5">
        <span className="flex gap-1.5">
          <i className="h-2.5 w-2.5 rounded-full bg-[#3a423c]" />
          <i className="h-2.5 w-2.5 rounded-full bg-[#3a423c]" />
          <i
            className={`h-2.5 w-2.5 rounded-full ${
              accent ? "bg-amber-400 pulse-soft" : "bg-[#3a423c]"
            }`}
          />
        </span>
        <span
          className={`text-[11px] font-semibold tracking-[0.18em] uppercase ${
            accent ? "text-amber-300/90" : "text-[#6b756e]"
          }`}
        >
          {title}
        </span>
      </div>
      <pre className="overflow-x-auto whitespace-pre text-[#c7cfc9]">
        {code.split("\n").map((line, i) => (
          <div key={i} className={line.includes("★") ? "text-amber-300" : ""}>
            {line}
          </div>
        ))}
      </pre>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Страница                                                           */
/* ------------------------------------------------------------------ */

export default function Page() {
  return (
    <main className="relative min-h-screen overflow-hidden">
      {/* фон */}
      <div className="grid-bg pointer-events-none absolute inset-x-0 top-0 h-[720px]" />
      <div className="pointer-events-none absolute -top-40 left-1/2 h-[420px] w-[720px] -translate-x-1/2 rounded-full bg-amber-500/[0.07] blur-[120px]" />

      <div className="relative mx-auto max-w-6xl px-5 pb-24 sm:px-8">
        {/* ─────────── header ─────────── */}
        <header className="flex items-center justify-between py-6">
          <div className="flex items-center gap-3">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl border border-amber-500/30 bg-amber-500/10 text-amber-400">
              <FolderGit2 size={18} />
            </span>
            <div className="font-mono2 text-[13px] tracking-wide text-[#8a948d]">
              DeilDevV / <span className="text-[#e8ece9]">Foxaria-main</span>
            </div>
          </div>
          <div className="hidden items-center gap-2 font-mono2 text-[12px] text-[#6b756e] sm:flex">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
            gradle multi-module · paper 1.21
          </div>
        </header>

        {/* ─────────── hero ─────────── */}
        <section className="pt-14 pb-16 sm:pt-20">
          <div className="rise font-mono2 mb-5 inline-flex items-center gap-2 rounded-full border border-amber-500/25 bg-amber-500/[0.08] px-3.5 py-1.5 text-[11.5px] tracking-[0.14em] text-amber-300 uppercase">
            <CheckCircle2 size={13} />
            v7 · кузница предметов · вайп · приваты · RTP
          </div>
          <h1 className="rise rise-1 max-w-3xl text-4xl leading-[1.08] font-bold tracking-tight sm:text-6xl">
            Порядок в репозитории.{" "}
            <span className="text-transparent bg-gradient-to-r from-amber-300 to-orange-500 bg-clip-text">
              Код не тронут.
            </span>
          </h1>
          <p className="rise rise-2 mt-6 max-w-2xl text-[15.5px] leading-relaxed text-[#9aa49d] sm:text-lg">
            Модули переехали в <b className="text-[#e8ece9]">modules/</b>, серверы — в{" "}
            <b className="text-[#e8ece9]">servers/</b>, сайт — в <b className="text-[#e8ece9]">web/site/</b>.
            Все переносы сделаны через <span className="font-mono2 text-amber-300">git mv</span>, а
            имена проектов Gradle остались прежними — сборка, деплой и запуск работают как раньше.
          </p>

          <div className="rise rise-3 mt-9 flex flex-wrap items-center gap-3">
            <Link
              href="/downloads/Foxaria-restructure-kit.zip"
              className="group inline-flex items-center gap-2.5 rounded-xl bg-amber-500 px-5 py-3 text-[14px] font-semibold text-black transition hover:bg-amber-400"
            >
              <Download size={16} className="transition-transform group-hover:translate-y-0.5" />
              Комплект v7 · 388 КБ
            </Link>
            <Link
              href="/downloads/Foxaria-main-restructured.zip"
              className="inline-flex items-center gap-2.5 rounded-xl border border-[#2a322c] bg-[#141816] px-5 py-3 text-[14px] font-semibold text-[#cfd6d1] transition hover:border-amber-500/40 hover:text-amber-200"
            >
              <FolderTree size={16} />
              Готовый репозиторий · 24 МБ
            </Link>
          </div>

          {/* статы */}
          <div className="rise rise-4 mt-12 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {STATS.map((s) => (
              <div
                key={s.label}
                className="rounded-2xl border border-[#1e2420] bg-[#101311]/80 px-4 py-4"
              >
                <s.icon size={16} className="mb-3 text-amber-400/80" />
                <div className="text-2xl font-bold tracking-tight">{s.value}</div>
                <div className="mt-1 text-[12px] leading-snug text-[#8a948d]">{s.label}</div>
              </div>
            ))}
          </div>
        </section>

        {/* ─────────── дерево ─────────── */}
        <section className="py-10">
          <div className="mb-7 flex items-end justify-between gap-4">
            <div>
              <div className="font-mono2 mb-2 text-[11px] tracking-[0.2em] text-amber-400/70 uppercase">
                01 · Структура
              </div>
              <h2 className="text-2xl font-bold tracking-tight sm:text-3xl">Было → стало</h2>
            </div>
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <TreePanel title="до — всё в корне" tone="dim" code={BEFORE_TREE} />
            <TreePanel title="после — по своим местам" tone="accent" code={AFTER_TREE} />
          </div>
        </section>

        {/* ─────────── карта переездов ─────────── */}
        <section className="py-10">
          <div className="font-mono2 mb-2 text-[11px] tracking-[0.2em] text-amber-400/70 uppercase">
            02 · Карта переездов
          </div>
          <h2 className="mb-7 text-2xl font-bold tracking-tight sm:text-3xl">
            Что куда переехало
          </h2>
          <div className="space-y-4">
            {MOVES.map((g) => (
              <div
                key={g.group}
                className="overflow-hidden rounded-2xl border border-[#1e2420] bg-[#101311]/80"
              >
                <div className="flex items-center gap-2.5 border-b border-[#1e2420] px-5 py-3.5">
                  <g.icon size={15} className="text-amber-400/80" />
                  <span className="text-[13px] font-semibold tracking-wide text-[#cfd6d1]">
                    {g.group}
                  </span>
                </div>
                <div className="divide-y divide-[#171c19]">
                  {g.rows.map(([from, to, note]) => (
                    <div
                      key={from}
                      className="grid items-center gap-1 px-5 py-3 font-mono2 text-[12.5px] sm:grid-cols-[1fr_auto_1fr] sm:gap-3"
                    >
                      <span className="text-[#e0877a]">{from}</span>
                      <ArrowRight size={14} className="hidden text-[#4c564f] sm:block" />
                      <span className="sm:text-right">
                        <span className="text-emerald-300/90">{to}</span>
                        {note && (
                          <span className="ml-2 font-sans text-[12px] text-[#78837c]">{note}</span>
                        )}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* ─────────── починено по ходу ─────────── */}
        <section className="py-10">
          <div className="font-mono2 mb-2 flex items-center gap-2 text-[11px] tracking-[0.2em] text-amber-400/70 uppercase">
            <Wrench size={12} />
            03 · Починено по ходу
          </div>
          <h2 className="mb-7 text-2xl font-bold tracking-tight sm:text-3xl">
            Найденные скрытые поломки
          </h2>
          <div className="grid gap-3 sm:grid-cols-2">
            {FIXES.map((f, i) => (
              <div
                key={f.title}
                className="rounded-2xl border border-[#1e2420] bg-[#101311]/80 p-5 transition-colors hover:border-[#2c352f]"
              >
                <div className="mb-2.5 flex items-center gap-2.5">
                  <span className="font-mono2 flex h-6 w-6 items-center justify-center rounded-md border border-amber-500/25 bg-amber-500/[0.08] text-[11px] text-amber-300">
                    {String(i + 1).padStart(2, "0")}
                  </span>
                  <h3 className="text-[14.5px] font-semibold text-[#e8ece9]">{f.title}</h3>
                </div>
                <p className="text-[13px] leading-relaxed text-[#8a948d]">{f.text}</p>
              </div>
            ))}
          </div>
        </section>

        {/* ─────────── как применить ─────────── */}
        <section className="py-10">
          <div className="font-mono2 mb-2 flex items-center gap-2 text-[11px] tracking-[0.2em] text-amber-400/70 uppercase">
            <TerminalSquare size={12} />
            04 · Применение
          </div>
          <h2 className="mb-7 text-2xl font-bold tracking-tight sm:text-3xl">
            Три шага на твоей машине
          </h2>
          <div className="rounded-2xl border border-amber-500/25 bg-[#141109]/80 p-5 sm:p-7">
            <ol className="space-y-5">
              {APPLY_STEPS.map((s, i) => (
                <li key={i} className="flex gap-4">
                  <span className="font-mono2 mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-amber-500 text-[13px] font-bold text-black">
                    {i + 1}
                  </span>
                  <div className="min-w-0">
                    <code className="font-mono2 block overflow-x-auto rounded-lg border border-[#2a2a16] bg-black/40 px-3.5 py-2.5 text-[12.5px] whitespace-nowrap text-amber-100">
                      {s.cmd}
                    </code>
                    <p className="mt-1.5 text-[12.5px] text-[#8a948d]">{s.hint}</p>
                  </div>
                </li>
              ))}
            </ol>
            <p className="mt-6 border-t border-[#2a2a16] pt-5 text-[13px] leading-relaxed text-[#9aa49d]">
              Скрипт идемпотентен: уже перенесённое пропустит. Миры, серверные jar-файлы и
              untracked-файлы не трогаются. После применения:{" "}
              <span className="font-mono2 text-[#cfd6d1]">git add -A && git commit</span> — и можно
              пушить.
            </p>
          </div>
        </section>

        {/* ─────────── заметка о безопасности ─────────── */}
        <section className="py-10">
          <div className="flex gap-4 rounded-2xl border border-yellow-600/25 bg-yellow-500/[0.05] p-5 sm:p-6">
            <ShieldAlert size={20} className="mt-0.5 shrink-0 text-yellow-400/90" />
            <div>
              <h3 className="mb-1.5 text-[14.5px] font-semibold text-yellow-200">
                По ходу заметил: секреты в открытом виде
              </h3>
              <p className="max-w-3xl text-[13px] leading-relaxed text-[#a89f8a]">
                В репозитории лежат пароль MySQL (<span className="font-mono2">deploy/ecosystem.config.js</span>,{" "}
                <span className="font-mono2">web/site/config.js</span>) и SSH-пароль сервера (
                <span className="font-mono2">deploy/server-config.json</span>). Я их не трогал, чтобы не
                сломать прод, — но раз репо публичный, пароли стоит считать скомпрометированными:
                вынеси в переменные окружения и обязательно смени.
              </p>
            </div>
          </div>
        </section>

        {/* ─────────── footer ─────────── */}
        <footer className="mt-8 flex flex-col items-start justify-between gap-3 border-t border-[#171c19] pt-8 sm:flex-row sm:items-center">
          <div className="font-mono2 text-[12px] text-[#5f6a62]">
            foxaria-main · structure cleanup · история git сохранена целиком
          </div>
          <div className="font-mono2 text-[12px] text-[#5f6a62]">
            modules {`//`} servers {`//`} web {`//`} deploy
          </div>
        </footer>
      </div>
    </main>
  );
}
