#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES texUnit;
uniform float opacity;
uniform float gamma;
varying vec2 UV;

void main() {
    vec4 color = texture2D(texUnit, UV);
    vec2 adjustedUV = vec2(UV.x, UV.y * 2.0);

    float radius = 0.12;
    vec2 center = vec2(radius + 0.05, 1.9 - radius);
    float dist = distance(adjustedUV, center);

    float innerRadius = radius * 0.95;
    float innerDist = distance(adjustedUV, vec2(center.x + 0.08, center.y - 0.028));

    if (dist < radius && innerDist > innerRadius) {
        vec3 rgb2 = pow(color.rgb * opacity * 0.91, vec3(gamma));
        gl_FragColor = vec4(rgb2, 1.0);
    } else {
        vec3 rgb = pow(color.rgb * opacity, vec3(gamma));
        gl_FragColor = vec4(rgb, 1.0);
    }
}