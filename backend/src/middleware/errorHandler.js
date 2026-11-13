export class ApiError extends Error {
  constructor(statusCode, code, message, details = undefined) {
    super(message);
    this.name = 'ApiError';
    this.statusCode = statusCode;
    this.code = code;
    this.details = details;
  }
}

export function notFoundHandler(req, res) {
  res.status(404).json({
    error: {
      code: 'NOT_FOUND',
      message: `Route ${req.method} ${req.originalUrl} not found`
    }
  });
}

export function errorHandler(err, req, res, _next) {
  if (err instanceof ApiError) {
    return res.status(err.statusCode).json({
      error: {
        code: err.code,
        message: err.message,
        ...(err.details ? { details: err.details } : {})
      }
    });
  }

  const isProd = process.env.NODE_ENV === 'production';
  console.error('[CobraDash3D-Backend Error]', err?.stack || err);

  return res.status(500).json({
    error: {
      code: 'INTERNAL_SERVER_ERROR',
      message: isProd ? 'An unexpected server error occurred' : err?.message || 'Internal error'
    }
  });
}
