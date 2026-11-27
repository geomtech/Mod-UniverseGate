#version 150

uniform sampler2D Sampler0;
uniform vec4 SceneRect;
uniform vec4 ScreenRect;
uniform float Phase;
uniform float Approach;
in vec2 texCoord0;
out vec4 fragColor;

vec3 scene(vec2 uv) {
    return texture(Sampler0, SceneRect.xy + SceneRect.zw * clamp(uv, 0.0, 1.0)).rgb;
}

void main() {
    vec2 p = texCoord0 - vec2(0.5);
    float radius = length(p) * 2.0;
    float coverage = sin(texCoord0.x * 3.14159) * sin(texCoord0.y * 3.14159);
    float diagonal = p.y + 0.36 * p.x;
    float swell = sin(22.0 * diagonal - 4.0 * Phase + 0.65 * sin(9.0 * p.x + Phase));
    float crossFlow = sin(15.0 * p.x - 7.0 * p.y - 3.0 * Phase);
    vec2 screenUV = (gl_FragCoord.xy - ScreenRect.xy) / ScreenRect.zw;
    vec2 offset = p * (0.032 + 0.027 * Approach) * coverage * swell;
    offset += coverage * (0.026 + 0.026 * Approach) *
        vec2(0.82 * swell + 0.26 * crossFlow, 0.55 * crossFlow - 0.35 * swell);
    // Retain a margin around the sampled rectangle when the aperture straddles the camera.
    vec2 margin = min(screenUV, 1.0 - screenUV);
    vec2 samplingFade = smoothstep(vec2(0.0), vec2(0.08), margin);
    vec2 uv = screenUV + offset * samplingFade;

    // The destination stays readable in grey; its blue sky is swapped for black space.
    vec3 color = scene(uv);
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float sky = smoothstep(0.06, 0.20, color.b - color.r) * smoothstep(0.25, 0.50, luminance);
    float light = smoothstep(0.03, 0.90, luminance);
    float graded = light * (0.50 + 0.18 * Approach) * (1.0 - sky);

    // Sparse 16px-grid stars, drifting slowly towards the centre of the passage.
    vec2 starGrid = floor((p * (1.0 + 0.04 * sin(Phase * 0.5)) + 0.5) * 48.0);
    float seed = fract(sin(dot(starGrid, vec2(12.9898, 78.233))) * 43758.5453);
    float twinkle = 0.55 + 0.45 * sin(Phase * 3.0 + seed * 40.0);
    float star = step(0.985, seed) * twinkle * (0.35 + 0.65 * fract(seed * 91.7))
        * (1.0 - smoothstep(0.04, 0.14, graded));

    // Faint grey mist folds keep a sense of motion without lifting the black floor.
    float folds = 0.5 + 0.5 * sin(14.0 * diagonal - 2.0 * Phase + 0.85 * crossFlow);
    float mist = smoothstep(0.55, 0.98, folds) * (0.020 + 0.015 * Approach);
    float vignette = smoothstep(0.62, 1.0, radius);
    float tone = mix(graded + mist + star * 0.85, 0.0, vignette);
    fragColor = vec4(vec3(tone), 1.0);
}
