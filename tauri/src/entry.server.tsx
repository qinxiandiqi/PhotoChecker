import { PassThrough } from 'node:stream'
import type { EntryContext } from 'react-router'
import { createReadableStreamFromReadable } from '@react-router/node'
import { ServerRouter } from 'react-router'
import { renderToPipeableStream } from 'react-dom/server'

export default function handleRequest(
  request: globalThis.Request,
  responseStatusCode: number,
  responseHeaders: globalThis.Headers,
  routerContext: EntryContext
) {
  return new Promise((resolve, reject) => {
    const { pipe } = renderToPipeableStream(
      <ServerRouter context={routerContext} url={request.url} />,
      {
        onShellReady() {
          responseHeaders.set('Content-Type', 'text/html')

          const body = new PassThrough()
          const stream = createReadableStreamFromReadable(body)

          resolve(
            new globalThis.Response(stream, {
              headers: responseHeaders,
              status: responseStatusCode,
            })
          )

          pipe(body)
        },
        onShellError(error: unknown) {
          reject(error)
        },
      }
    )
  })
}
