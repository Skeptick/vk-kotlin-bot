VK Kotlin Bot
=============
Библиотека, написанная на языке Kotlin и предоставляющая DSL-подобный синтаксис для создания чат-бота для [vk.com](https://vk.com).

Использует:
  - [kotlinx.coroutines] - для организации параллелизма
  - [kotlinx.serialization] - для десериализации ответов от API Вконтакте
  - [Fuel] - для сетевых запросов
  - [Logback] - для логгирования
-----
На данный момент поддерживается обработка следующих событий от LongPolling сервера:
  - Сообщения из чата (беседы) / от пользователя / от сообщества
  - Создание чата
  - Смена заголовка чата
  - Изменение / удаление фото чата
  - Приглашение / исключение пользователя в / из чата
  
Реализованы некоторые базовые методы для работы с API Вконтакте, такие как: отправка сообщений, получение сообщений по ID, получение списка пользователей в чате и т.д.

Пример использования
--------------------
```kotlin
fun main(args: Array<String>) = runBlocking {
    val bot = BotApplication("YOUR_ACCESS_TOKEN")
    bot.anyMessage { main() }
    bot.onChatPhotoRemove { /* ... */ }
    bot.onChatKickUser { /* ... */ }
    bot.run()
}

fun DefaultMessageRoute.main() {

    onMessageFrom<Chat> {
        // сюда пройдут только сообщения из чата
        // помимо <Chat> доступны параметры User и Community
            
        onIncomingMessage {
            // сюда пройдут только входящие сообщения из чата
              
            onMessage("привет") {
                // только входящие сообщения из чата 
                // начинающиеся с "привет"
        
                handle { /* код в этом блоке отработает в любом случае */ }
            
                onMessage("бот", "робот") { /* ... */ }
            
                onMessage("как дела") { /* ... */ }
         
                intercept { 
                    // код в этом блоке отработает, 
                    // только если событие не удовлетворяет 
                    // ни одному другому маршруту.
          
                    it.message.respondWithForward("Неизвестная команда!")
                }
        
            }
      
        }
    
    }
  
}
```

[Реальный бот], написанный с использованием библиотеки (на данный момент есть несколько команд работающих в групповых беседах).
Его реализация находится в директории `/app`.

Получение истории событий
-------------------------
Для получения LongPolling истории (сообытий, произошедших во время неактивности бота), передайте функции `onHistoryLoaded(Long)` последнее значение `pts`, полученное до момента остановки бота.  
Это значение приходит с каждым LongPolling запросом и передается телу лямбды `onPtsUpdated`. Более наглядно работу с историей можно посмотреть в коде бота.

Очередь событий
---------------
Каждый диалог имеет свою очередь событий, т.е., при наличии двух сообщений из одного чата, адресованных боту, второе сообщение не будет обработано, пока не обработано первое. В связи с этим, долгие операции и операции, где порядок обработки не играет роли, рекомендуется выносить в отдельный поток, например, заключать в корутинный блок `launch { }`.

License
=======

    Copyright 2017 Danil Yudov
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
       
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
    
   [Реальный бот]: <https://vk.com/bethoven.olegovich>
   [kotlinx.coroutines]: <https://github.com/Kotlin/kotlinx.coroutines>
   [kotlinx.serialization]: <https://github.com/Kotlin/kotlinx.serialization>
   [Fuel]: <https://github.com/kittinunf/Fuel>
   [Logback]: <https://github.com/qos-ch/logback>